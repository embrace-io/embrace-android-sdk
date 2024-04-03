package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbraceAttribute
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl.Companion.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.sdk.trace.IdGenerator
import java.util.concurrent.ConcurrentLinkedQueue

internal class FakePersistableEmbraceSpan(
    override val parent: EmbraceSpan?,
    val name: String? = null,
    val type: TelemetryType = EmbType.Performance.Default,
    val internal: Boolean = false,
    private val fakeClock: FakeClock = FakeClock()
) : PersistableEmbraceSpan {

    val attributes = mutableMapOf(type.toOTelKeyValuePair())
    val events = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    var started = false
    var stopped = false
    var errorCode: ErrorCode? = null

    private var spanStartTimeMs: Long? = null
    private var spanEndTimeMs: Long? = null
    private var status = Span.Status.UNSET

    override var traceId: String? = parent?.traceId

    override var spanId: String? = null
    override val isRecording: Boolean
        get() = started && !stopped

    override fun start(): Boolean = start(startTimeMs = null)

    override fun start(startTimeMs: Long?): Boolean {
        if (!started) {
            spanId = IdGenerator.random().generateSpanId()
            if (parent == null) {
                traceId = IdGenerator.random().generateTraceId()
            }
            spanStartTimeMs = startTimeMs ?: fakeClock.now()
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
                setEmbraceAttribute(errorCode.fromErrorCode())
                Span.Status.ERROR
            }
            spanEndTimeMs = endTimeMs ?: fakeClock.now()
            stopped = true
        }
        return true
    }

    override fun addEvent(name: String): Boolean = addEvent(name)

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

    override fun addAttribute(key: String, value: String): Boolean {
        attributes[key] = value
        return true
    }

    override fun snapshot(): Span? {
        return if (spanId == null) {
            null
        } else {
            Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId,
                name = name,
                startTimeUnixNano = spanStartTimeMs?.millisToNanos(),
                endTimeUnixNano = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = events.map(EmbraceSpanEvent::toNewPayload),
                attributes = attributes.toNewPayload()
            )
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
        attributes.hasEmbraceAttribute(embraceAttribute)

    /**
     * Should only used if this is being used to fake a session span
     */
    fun addCustomBreadcrumb(name: String, timestampMs: Long?) {
        val customBreadcrumb = SpanEventData(
            schemaType = SchemaType.CustomBreadcrumb(name),
            spanStartTimeMs = timestampMs ?: fakeClock.now()
        )
        addEvent(customBreadcrumb.schemaType.defaultName, customBreadcrumb.spanStartTimeMs, attributes)
    }

    companion object {
        fun notStarted(parent: EmbraceSpan? = null): FakePersistableEmbraceSpan =
            FakePersistableEmbraceSpan(
                name = "not-started",
                parent = parent
            )

        fun started(parent: EmbraceSpan? = null): FakePersistableEmbraceSpan {
            val span = FakePersistableEmbraceSpan(
                name = "started",
                parent = parent
            )
            span.start()
            return span
        }

        fun stopped(parent: EmbraceSpan? = null): FakePersistableEmbraceSpan {
            val span = FakePersistableEmbraceSpan(
                name = "stopped",
                parent = parent
            )
            span.start()
            span.stop()
            return span
        }
    }
}
