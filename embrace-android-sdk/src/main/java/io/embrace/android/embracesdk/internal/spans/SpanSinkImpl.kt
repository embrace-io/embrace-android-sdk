package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

internal class SpanSinkImpl : SpanSink {
    private val completedSpans: Queue<EmbraceSpanData> = ConcurrentLinkedQueue()
    private val spansToFlush = AtomicReference<List<EmbraceSpanData>>(listOf())

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode {
        try {
            completedSpans += spans.map { it.toEmbraceSpanData() }
        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun completedSpans(): List<EmbraceSpanData> {
        val spansToReturn = completedSpans.size
        return completedSpans.threadSafeTake(spansToReturn)
    }

    override fun flushSpans(): List<EmbraceSpanData> {
        synchronized(spansToFlush) {
            spansToFlush.set(completedSpans())
            completedSpans.removeAll(spansToFlush.get().toSet())
            return spansToFlush.get()
        }
    }
}
