package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.session.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.attrs.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.otel.schema.TelemetryType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.toStatus
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.StatusCode
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class FakeEmbraceSdkSpan(
    var name: String = "fake-span",
    var parentContext: Context = Context.root(),
    val type: TelemetryType = EmbType.Performance.Default,
    val internal: Boolean = false,
    val private: Boolean = internal,
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    private val fakeClock: FakeClock = FakeClock(),
) : EmbraceSdkSpan {

    private var sdkSpan: io.opentelemetry.api.trace.Span? = null
    var spanStartTimeMs: Long? = null
    var spanEndTimeMs: Long? = null
    var status: Span.Status = Span.Status.UNSET
    var statusDescription: String = ""
    var errorCode: ErrorCode? = null
    val attributes: MutableMap<String, String> = mutableMapOf(type.asPair())
    val events: ConcurrentLinkedQueue<EmbraceSpanEvent> = ConcurrentLinkedQueue()
    val links: ConcurrentLinkedQueue<Link> = ConcurrentLinkedQueue()

    override val parent: EmbraceSpan?
        get() = parentContext.getEmbraceSpan()

    override val spanContext: SpanContext?
        get() = sdkSpan?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = sdkSpan?.isRecording ?: false

    override fun start(): Boolean = start(startTimeMs = null)

    override fun start(startTimeMs: Long?): Boolean {
        if (!started()) {
            val timestampMs = startTimeMs ?: fakeClock.now()
            sdkSpan = FakeSpanBuilder(name)
                .setStartTimestamp(timestampMs, TimeUnit.MILLISECONDS)
                .setParent(parentContext)
                .startSpan()
            spanStartTimeMs = timestampMs
        }
        return true
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        if (isRecording) {
            this.errorCode = errorCode
            if (errorCode != null) {
                setStatus(StatusCode.Error(null))
            }

            if (status == Span.Status.ERROR) {
                val error = errorCode?.fromErrorCode() ?: ErrorCodeAttribute.Failure
                setSystemAttribute(error.key.name, error.value)
            }

            val timestamp = endTimeMs ?: fakeClock.now()
            checkNotNull(sdkSpan).end(timestamp, TimeUnit.MILLISECONDS)
            spanEndTimeMs = timestamp
        }
        return true
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean {
        events.add(
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: fakeClock.now(),
                attributes = attributes
            )
        )
        return true
    }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        addEvent(InstrumentedConfigImpl.otelLimits.getExceptionEventName(), null, attributes)

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addEvent(name, timestampMs, attributes)

    override fun removeSystemEvents(type: EmbType): Boolean {
        events.removeAll { it.hasEmbraceAttribute(type) }
        return true
    }

    override fun setStatus(statusCode: StatusCode, description: String) {
        status = statusCode.toStatus()
        statusDescription = description
    }

    override fun getStartTimeMs(): Long? = spanStartTimeMs

    override fun addAttribute(key: String, value: String): Boolean {
        attributes[key] = value
        return true
    }

    override fun updateName(newName: String): Boolean {
        name = newName
        return true
    }

    override fun addLink(linkedSpanContext: SpanContext, attributes: Map<String, String>?): Boolean {
        links.add(Link(linkedSpanContext.spanId, attributes?.toEmbracePayload()))
        return true
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, attributes: Map<String, String>): Boolean =
        addLink(linkedSpanContext, attributes)

    override fun asNewContext(): Context? = sdkSpan?.let { parentContext.with(this).with(it) }

    override fun snapshot(): Span? {
        return if (spanId == null) {
            null
        } else {
            Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId,
                name = name,
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = events.map(EmbraceSpanEvent::toEmbracePayload),
                attributes = attributes.toEmbracePayload(),
                links = links.toList()
            )
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
        attributes.hasEmbraceAttribute(embraceAttribute)

    override fun getSystemAttribute(key: String): String? = attributes[key]

    override fun setSystemAttribute(key: String, value: String) {
        addSystemAttribute(key, value)
    }

    override fun addSystemAttribute(key: String, value: String) {
        attributes[key] = value
    }

    override fun removeSystemAttribute(key: String) {
        attributes.remove(key)
    }

    private fun started(): Boolean = sdkSpan != null

    companion object {
        fun notStarted(): FakeEmbraceSdkSpan = FakeEmbraceSdkSpan(name = "not-started")

        fun started(
            parent: EmbraceSdkSpan? = null,
            parentContext: Context = parent?.run { parent.asNewContext() } ?: Context.root(),
            clock: FakeClock = FakeClock(),
        ): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "started",
                fakeClock = clock,
                parentContext = parentContext
            ).apply {
                start()
            }

        fun stopped(): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(name = "stopped").apply {
                start()
                stop()
            }

        fun sessionSpan(
            sessionId: String,
            startTimeMs: Long,
            lastHeartbeatTimeMs: Long?,
            endTimeMs: Long? = null,
            sessionProperties: Map<String, String>? = null,
            processIdentifier: String = "fake-process-id",
        ): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "emb-session",
                type = EmbType.Ux.Session
            ).apply {
                start(startTimeMs)
                sessionProperties?.forEach {
                    addSystemAttribute(
                        key = it.key.toSessionPropertyAttributeName(),
                        value = it.value
                    )
                }

                setSystemAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)
                setSystemAttribute(embProcessIdentifier.name, processIdentifier)
                setSystemAttribute(embState.name, "foreground")
                setSystemAttribute(
                    embHeartbeatTimeUnixNano.name,
                    (lastHeartbeatTimeMs ?: this.spanStartTimeMs)!!.millisToNanos().toString()
                )
                if (endTimeMs != null) {
                    stop(endTimeMs)
                }
            }
    }
}
