package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class SpanSinkImpl : SpanSink {
    private val completedSpans: Queue<EmbraceSpanData> = ConcurrentLinkedQueue()
    private val flushLock = Any()

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
        synchronized(flushLock) {
            val flushed = completedSpans()
            completedSpans.removeAll(flushed.toSet())
            return flushed
        }
    }
}
