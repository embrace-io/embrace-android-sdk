package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

internal interface SpansSink {
    /**
     * Store the given list of completed Spans to be sent to the backend at the next available time
     */
    fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode

    /**
     * Return the list of the currently stored completed Spans that have not been sent to the backend
     */
    fun completedSpans(): List<EmbraceSpanData>

    /**
     * Flush and return all of the stored completed Spans. This should be called when the stored completed spans are ready to be sent to
     * the backend.
     */
    fun flushSpans(): List<EmbraceSpanData>

    /**
     * Return the [EmbraceSpan] for the given [spanId] if it's available
     */
    fun getSpan(spanId: String): EmbraceSpan?

    fun getSpansRepository(): SpansRepository?
}
