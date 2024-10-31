package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.BREADCRUMBS_CUSTOM
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.BREADCRUMBS_TAPS
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.opentelemetry.api.trace.SpanId

internal class SpanSanitizer(
    private val spans: List<Span>?,
    private val enabledComponents: Set<String>,
) : Sanitizable<List<Span>> {

    override fun sanitize(): List<Span>? {
        if (spans == null) {
            return null
        }

        val sanitizedSpans = spans.filter(::sanitizeSpans).toMutableList()
        val sessionSpan =
            sanitizedSpans.singleOrNull { it.name == "emb-session" } ?: return spans
        sanitizedSpans.remove(sessionSpan)

        val sanitizedEvents = sessionSpan.events?.filter(::sanitizeEvents)

        val sanitizedSessionSpan = Span(
            sessionSpan.traceId,
            sessionSpan.spanId,
            sessionSpan.parentSpanId ?: SpanId.getInvalid(),
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

    private fun sanitizeSpans(span: Span): Boolean {
        return when {
            span.hasFixedAttribute(EmbType.Ux.View) && !shouldAddViewBreadcrumbs() -> false
            span.name == "emb-thread-blockage" && !shouldSendANRs() -> false
            else -> true
        }
    }

    private fun sanitizeEvents(event: SpanEvent): Boolean {
        return !(
            (event.hasFixedAttribute(EmbType.System.Breadcrumb) && !shouldAddCustomBreadcrumbs()) ||
                (event.hasFixedAttribute(EmbType.Ux.Tap) && !shouldAddTapBreadcrumbs()) ||
                (event.hasFixedAttribute(EmbType.Ux.WebView) && !shouldAddWebViewUrls())
            )
    }

    private fun shouldSendANRs() =
        enabledComponents.contains(SessionGatingKeys.PERFORMANCE_ANR)

    private fun shouldAddCustomBreadcrumbs() = enabledComponents.contains(BREADCRUMBS_CUSTOM)

    private fun shouldAddTapBreadcrumbs() = enabledComponents.contains(BREADCRUMBS_TAPS)

    private fun shouldAddViewBreadcrumbs() =
        enabledComponents.contains(SessionGatingKeys.BREADCRUMBS_VIEWS)

    private fun shouldAddWebViewUrls() =
        enabledComponents.contains(SessionGatingKeys.BREADCRUMBS_WEB_VIEWS)
}
