package io.embrace.android.embracesdk.benchmark.fakes

import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * An [EmbraceSpan] that keeps what is done to it, timed by [clock] when it is given no time. Like the SDK's spans,
 * it starts and stops once, and takes attributes and events only while it is recording. It models what scenarios do
 * with a span, and anything else fails.
 */
@OptIn(ExperimentalApi::class)
internal class FakeEmbraceSpan(
    val name: String,
    override val parent: EmbraceSpan?,
    override val autoTerminationMode: AutoTerminationMode,
    private val clock: () -> Long,
) : EmbraceSpan {

    var startTimeMs: Long? = null
        private set

    var endTimeMs: Long? = null
        private set

    var errorCode: ErrorCode? = null
        private set

    val attributes: MutableMap<String, String> = mutableMapOf()
    val events: MutableList<Event> = mutableListOf()

    override val spanContext: SpanContext? = null
    override val traceId: String? = null
    override val spanId: String? = null

    override val isRecording: Boolean
        get() = startTimeMs != null && endTimeMs == null

    override fun start(startTimeMs: Long?): Boolean {
        if (this.startTimeMs != null) {
            return false
        }
        this.startTimeMs = startTimeMs ?: clock()
        return true
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        if (!isRecording) {
            return false
        }
        this.errorCode = errorCode
        this.endTimeMs = endTimeMs ?: clock()
        return true
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>): Boolean {
        if (!isRecording) {
            return false
        }
        events += Event(name, timestampMs ?: clock(), attributes)
        return true
    }

    override fun addAttribute(key: String, value: String): Boolean {
        if (!isRecording) {
            return false
        }
        attributes[key] = value
        return true
    }

    override fun recordException(exception: Throwable, attributes: Map<String, String>): Boolean =
        error("recordException")

    override fun updateName(newName: String): Boolean = error("updateName")

    override fun addLink(linkedSpanContext: SpanContext, attributes: Map<String, String>): Boolean = error("addLink")

    data class Event(
        val name: String,
        val timestampMs: Long,
        val attributes: Map<String, String>,
    )
}
