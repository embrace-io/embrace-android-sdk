package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.opentelemetry.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl.Companion.EXCEPTION_EVENT_NAME
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.getEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toStatus
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

public class FakePersistableEmbraceSpan(
    public var name: String = "fake-span",
    public var parentContext: Context = Context.root(),
    public val type: TelemetryType = EmbType.Performance.Default,
    public val internal: Boolean = false,
    public val private: Boolean = internal,
    private val fakeClock: FakeClock = FakeClock(),
) : PersistableEmbraceSpan {

    private var sdkSpan: io.opentelemetry.api.trace.Span? = null
    public var spanStartTimeMs: Long? = null
    public var spanEndTimeMs: Long? = null
    public var status: Span.Status = Span.Status.UNSET
    public var statusDescription: String = ""
    public var errorCode: ErrorCode? = null
    public val attributes: MutableMap<String, String> = mutableMapOf(type.toEmbraceKeyValuePair())
    public val events: ConcurrentLinkedQueue<EmbraceSpanEvent> = ConcurrentLinkedQueue<EmbraceSpanEvent>()

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
                setStatus(StatusCode.ERROR)
            }

            if (status == Span.Status.ERROR) {
                val error = errorCode?.fromErrorCode() ?: ErrorCodeAttribute.Failure
                setSystemAttribute(error.key.attributeKey, error.value)
            }

            val timestamp = endTimeMs ?: fakeClock.now()
            checkNotNull(sdkSpan).end(timestamp, TimeUnit.MILLISECONDS)
            spanEndTimeMs = timestamp
        }
        return true
    }

    override fun addEvent(name: String): Boolean = addEvent(name, null, null)

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
        addEvent(EXCEPTION_EVENT_NAME, null, attributes)

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addEvent(name, timestampMs, attributes)

    override fun removeSystemEvents(type: EmbType): Boolean {
        events.removeAll { it.hasFixedAttribute(type) }
        return true
    }

    override fun setStatus(statusCode: StatusCode, description: String) {
        status = statusCode.toStatus()
        statusDescription = description
    }

    override fun addAttribute(key: String, value: String): Boolean {
        attributes[key] = value
        return true
    }

    override fun updateName(newName: String): Boolean {
        name = newName
        return true
    }

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
                events = events.map(EmbraceSpanEvent::toNewPayload),
                attributes = attributes.toNewPayload()
            )
        }
    }

    override fun hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
        attributes.hasFixedAttribute(fixedAttribute)

    override fun getSystemAttribute(key: AttributeKey<String>): String? = attributes[key.key]

    override fun setSystemAttribute(key: AttributeKey<String>, value: String) {
        addSystemAttribute(key.key, value)
    }

    override fun addSystemAttribute(key: String, value: String) {
        attributes[key] = value
    }

    override fun removeSystemAttribute(key: String) {
        attributes.remove(key)
    }

    private fun started(): Boolean = sdkSpan != null

    public companion object {
        public fun notStarted(): FakePersistableEmbraceSpan = FakePersistableEmbraceSpan(name = "not-started")

        public fun started(
            parent: PersistableEmbraceSpan? = null,
            parentContext: Context = parent?.run { parent.asNewContext() } ?: Context.root(),
            clock: FakeClock = FakeClock()
        ): FakePersistableEmbraceSpan =
            FakePersistableEmbraceSpan(
                name = "started",
                fakeClock = clock,
                parentContext = parentContext
            ).apply {
                start()
            }

        public fun stopped(): FakePersistableEmbraceSpan =
            FakePersistableEmbraceSpan(name = "stopped").apply {
                start()
                stop()
            }

        public fun sessionSpan(
            sessionId: String,
            startTimeMs: Long,
            lastHeartbeatTimeMs: Long?,
            endTimeMs: Long? = null
        ): FakePersistableEmbraceSpan =
            FakePersistableEmbraceSpan(
                name = "emb-session",
                type = EmbType.Ux.Session
            ).apply {
                start(startTimeMs)
                setSystemAttribute(SessionIncubatingAttributes.SESSION_ID, sessionId)
                setSystemAttribute(
                    embHeartbeatTimeUnixNano.attributeKey,
                    (lastHeartbeatTimeMs ?: this.spanStartTimeMs)!!.millisToNanos().toString()
                )
                if (endTimeMs != null) {
                    stop(endTimeMs)
                }
            }
    }
}
