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
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.k2j.tracing.SpanContextAdapter
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
class FakeEmbraceSdkSpan(
    var name: String = "fake-span",
    var parentContext: OtelJavaContext = OtelJavaContext.root(),
    val type: EmbType = EmbType.Performance.Default,
    val internal: Boolean = false,
    val private: Boolean = internal,
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    private val fakeClock: FakeClock = FakeClock(),
) : EmbraceSdkSpan {

    private var sdkSpan: OtelJavaSpan? = null
    var spanStartTimeMs: Long? = null
    var spanEndTimeMs: Long? = null
    var status: Span.Status = Span.Status.UNSET
    var statusDescription: String = ""
    var errorCode: ErrorCode? = null
    val attributes: MutableMap<String, String> = mutableMapOf(type.asPair())
    val events: ConcurrentLinkedQueue<EmbraceSpanEvent> = ConcurrentLinkedQueue()
    val links: ConcurrentLinkedQueue<EmbraceLinkData> = ConcurrentLinkedQueue()

    override val parent: EmbraceSpan?
        get() = parentContext.getEmbraceSpan()

    override val spanContext: OtelJavaSpanContext?
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
        status = statusCode.toEmbracePayload()
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

    override fun addLink(linkedSpanContext: OtelJavaSpanContext, attributes: Map<String, String>?): Boolean {
        links.add(EmbraceLinkData(SpanContextAdapter(linkedSpanContext), attributes ?: emptyMap()))
        return true
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean {
        links.add(EmbraceLinkData(linkedSpanContext, mutableMapOf(type.asPair()).apply { putAll(attributes) }))
        return true
    }

    override fun asNewContext(): OtelJavaContext? = sdkSpan?.let { parentContext.with(this).with(it) }

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
                links = links.toList().map { it.toEmbracePayload() }
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
            parentContext: OtelJavaContext = parent?.run { parent.asNewContext() } ?: OtelJavaContext.root(),
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
