package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class SpanSinkImpl : SpanSink {
    private val completedSpans: Queue<Span> = ConcurrentLinkedQueue()
    private val flushLock = Any()

    override fun storeCompletedSpans(spans: List<Span>): StoreDataResult {
        try {
            completedSpans += spans
        } catch (t: Throwable) {
            return StoreDataResult.FAILURE
        }
        return StoreDataResult.SUCCESS
    }

    override fun completedSpans(): List<Span> {
        val spansToReturn = completedSpans.size
        return completedSpans.threadSafeTake(spansToReturn)
    }

    override fun flushSpans(): List<Span> {
        synchronized(flushLock) {
            val flushed = completedSpans()
            completedSpans.removeAll(flushed.toSet())
            return flushed
        }
    }
}
