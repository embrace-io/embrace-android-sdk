package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl.Companion.EXCEPTION_EVENT_NAME
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toStatus
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.spans.fromErrorCode
import io.embrace.android.embracesdk.spans.getEmbraceSpan
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

internal class FakePersistableEmbraceSpan(
    var name: String = "fake-span",
    var parentContext: Context = Context.root(),
    val type: TelemetryType = EmbType.Performance.Default,
    val internal: Boolean = false,
    val private: Boolean = internal,
    private val fakeClock: FakeClock = FakeClock(),
) : PersistableEmbraceSpan {

    private var sdkSpan: io.opentelemetry.api.trace.Span? = null
    var spanStartTimeMs: Long? = null
    var spanEndTimeMs: Long? = null
    var status = Span.Status.UNSET
    var statusDescription = ""
    var errorCode: ErrorCode? = null
    val attributes = mutableMapOf(type.toEmbraceKeyValuePair())
    val events = ConcurrentLinkedQueue<EmbraceSpanEvent>()

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
                setSystemAttribute(error.key, error.value)
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

    override fun removeEvents(type: EmbType): Boolean {
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

    override fun getSystemAttribute(key: EmbraceAttributeKey): String? = attributes[key.name]

    override fun setSystemAttribute(key: EmbraceAttributeKey, value: String) {
        attributes[key.name] = value
    }

    override fun removeCustomAttribute(key: String): Boolean = attributes.remove(key) != null

    private fun started(): Boolean = sdkSpan != null

    companion object {
        fun notStarted(): FakePersistableEmbraceSpan = FakePersistableEmbraceSpan(name = "not-started")

        fun started(
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

        fun stopped(): FakePersistableEmbraceSpan =
            FakePersistableEmbraceSpan(name = "stopped").apply {
                start()
                stop()
            }
    }
}
