package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

internal class SpanSanitizer(
    private val spans: List<EmbraceSpanData>?,
    @Suppress("UnusedPrivateProperty") private val enabledComponents: Set<String>
) : Sanitizable<List<EmbraceSpanData>> {

    override fun sanitize(): List<EmbraceSpanData>? {
        if (spans == null) {
            return null
        }

        val sanitizedSpans = spans.filter(::sanitizeSpans).toMutableList()
        val sessionSpan =
            sanitizedSpans.singleOrNull { it.name == "emb-session-span" } ?: return spans
        sanitizedSpans.remove(sessionSpan)

        val sanitizedEvents = sessionSpan.events.filter(::sanitizeEvents)

        val sanitizedSessionSpan = EmbraceSpanData(
            sessionSpan.traceId,
            sessionSpan.spanId,
            sessionSpan.parentSpanId,
            sessionSpan.name,
            sessionSpan.startTimeNanos,
            sessionSpan.endTimeNanos,
            sessionSpan.status,
            sanitizedEvents,
            sessionSpan.attributes
        )
        sanitizedSpans.add(sanitizedSessionSpan)
        return sanitizedSpans
    }

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    private fun sanitizeSpans(span: EmbraceSpanData): Boolean {
        return true
    }

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    private fun sanitizeEvents(event: EmbraceSpanEvent): Boolean {
        return true
    }
}
