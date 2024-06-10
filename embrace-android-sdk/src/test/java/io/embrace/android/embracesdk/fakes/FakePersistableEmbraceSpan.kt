package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl.Companion.EXCEPTION_EVENT_NAME
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.IdGenerator
import java.util.concurrent.ConcurrentLinkedQueue

internal class FakePersistableEmbraceSpan(
    override val parent: EmbraceSpan?,
    var name: String = "fake-span",
    val type: TelemetryType = EmbType.Performance.Default,
    val internal: Boolean = false,
    val private: Boolean = internal,
    private val fakeClock: FakeClock = FakeClock(),
    var parentContext: Context = Context.root()
) : PersistableEmbraceSpan {

    val attributes = mutableMapOf(type.toEmbraceKeyValuePair())
    val events = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    var started = false
    var stopped = false
    var errorCode: ErrorCode? = null

    var spanStartTimeMs: Long? = null
    var spanEndTimeMs: Long? = null
    var status = Span.Status.UNSET
    private var sdkSpan: io.opentelemetry.api.trace.Span? = null

    override var spanContext: SpanContext? = null

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = started && !stopped

    override fun start(): Boolean = start(startTimeMs = null)

    override fun start(startTimeMs: Long?): Boolean {
        if (!started) {
            val spanTraceId = if (parent == null) {
                IdGenerator.random().generateTraceId()
            } else {
                parent.traceId
            }

            spanContext = SpanContext.create(
                checkNotNull(spanTraceId),
                IdGenerator.random().generateSpanId(),
                TraceFlags.getDefault(),
                TraceState.getDefault()
            )

            spanStartTimeMs = startTimeMs ?: fakeClock.now()
            sdkSpan = FakeSpanBuilder(name).setParent(parentContext).startSpan()
            started = true
        }
        return true
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        if (!stopped) {
            this.errorCode = errorCode
            status = if (errorCode == null) {
                Span.Status.OK
            } else {
                val code = errorCode.fromErrorCode()
                setSystemAttribute(code.key, code.value)
                Span.Status.ERROR
            }
            spanEndTimeMs = endTimeMs ?: fakeClock.now()
            stopped = true
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

    companion object {
        fun notStarted(parent: PersistableEmbraceSpan? = null, clock: FakeClock = FakeClock()): FakePersistableEmbraceSpan =
            FakePersistableEmbraceSpan(
                parent = parent,
                name = "not-started",
                fakeClock = clock,
            )

        fun started(
            parent: PersistableEmbraceSpan? = null,
            parentContext: Context = parent?.run { parent.asNewContext() } ?: Context.root(),
            clock: FakeClock = FakeClock()
        ): FakePersistableEmbraceSpan {
            val span = FakePersistableEmbraceSpan(
                parent = parent,
                name = "started",
                fakeClock = clock,
                parentContext = parentContext
            )
            span.start()
            return span
        }

        fun stopped(parent: PersistableEmbraceSpan? = null, clock: FakeClock = FakeClock()): FakePersistableEmbraceSpan {
            val span = FakePersistableEmbraceSpan(
                parent = parent,
                name = "stopped",
                fakeClock = clock,
            )
            span.start()
            span.stop()
            return span
        }
    }
}
