package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

class SpanSinkImpl : SpanSink {
    private val completedSpans: Queue<EmbraceSpanData> = ConcurrentLinkedQueue()
    private val spansToFlush = AtomicReference<List<EmbraceSpanData>>(listOf())

    override fun storeCompletedSpans(spans: List<EmbraceSpanData>): StoreDataResult {
        try {
            completedSpans += spans
        } catch (t: Throwable) {
            return StoreDataResult.FAILURE
        }
        return StoreDataResult.SUCCESS
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
