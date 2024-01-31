package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

internal class SpansSinkImpl : SpansSink {

    private val spansRepository = SpansRepository()

    /**
     * Spans that have finished, successfully or not, that will be sent with the next session or background activity payload. These
     * should be cached along with the other data in the payload.
     */
    private val completedSpans: MutableList<EmbraceSpanData> = mutableListOf()

    override fun getSpan(spanId: String): EmbraceSpan? = spansRepository.getSpan(spanId)

    override fun getSpansRepository(): SpansRepository = spansRepository

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode {
        try {
            synchronized(completedSpans) {
                completedSpans += spans.map { EmbraceSpanData(spanData = it) }
            }
        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun completedSpans(): List<EmbraceSpanData> {
        synchronized(completedSpans) {
            return completedSpans.toList()
        }
    }

    override fun flushSpans(): List<EmbraceSpanData> {
        synchronized(completedSpans) {
            val flushedSpans = completedSpans.toList()
            completedSpans.clear()
            return flushedSpans
        }
    }
}
