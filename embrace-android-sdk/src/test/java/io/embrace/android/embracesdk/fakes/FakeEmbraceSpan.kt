package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.sdk.trace.IdGenerator

internal class FakeEmbraceSpan(
    override val parent: EmbraceSpan?,
    val name: String? = null,
    val type: TelemetryType = EmbType.Performance.Default,
    val internal: Boolean = true
) : EmbraceSpan {

    val attributes = mutableMapOf<String, String>()
    val events = mutableListOf<SpanEventData>()

    private var started = false
    private var stopped = false
    private var errorCode: ErrorCode? = null

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
            started = true
        }
        return true
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        if (!stopped) {
            this.errorCode = errorCode
            stopped = true
        }
        return true
    }

    override fun addEvent(name: String): Boolean {
        events.add(SpanEventData(SchemaType.CustomBreadcrumb(name), 0))
        return true
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean {
        events.add(SpanEventData(SchemaType.CustomBreadcrumb(name), checkNotNull(timestampMs)))
        return true
    }

    override fun addAttribute(key: String, value: String): Boolean {
        attributes[key] = value
        return true
    }

    companion object {
        fun notStarted(parent: EmbraceSpan? = null): FakeEmbraceSpan = FakeEmbraceSpan(parent)

        fun started(parent: EmbraceSpan? = null): FakeEmbraceSpan {
            val span = notStarted(parent)
            span.start()
            return span
        }

        fun stopped(parent: EmbraceSpan? = null): FakeEmbraceSpan {
            val span = started(parent)
            span.stop()
            return span
        }
    }
}