package io.embrace.android.embracesdk.internal.spans

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

internal class SpansSinkImpl : SpansSink {
    private val completedSpans: MutableList<EmbraceSpanData> = mutableListOf()

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
