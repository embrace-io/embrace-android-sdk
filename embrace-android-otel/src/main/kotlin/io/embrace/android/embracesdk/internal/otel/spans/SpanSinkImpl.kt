package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.threadSafeToList
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

    override fun completedSpans(): List<Span> = completedSpans.threadSafeToList()

    override fun flushSpans(): List<Span> {
        synchronized(flushLock) {
            val count = completedSpans.size
            val flushed = ArrayList<Span>(count)
            repeat(count) { completedSpans.poll()?.let(flushed::add) }
            return flushed
        }
    }
}
