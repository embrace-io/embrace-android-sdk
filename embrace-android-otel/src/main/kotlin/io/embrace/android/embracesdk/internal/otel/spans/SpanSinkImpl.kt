package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.utils.threadSafeToList
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

    override fun completedSpans(): List<EmbraceSpanData> = completedSpans.threadSafeToList()

    override fun flushSpans(): List<EmbraceSpanData> {
        synchronized(flushLock) {
            val count = completedSpans.size
            val flushed = ArrayList<EmbraceSpanData>(count)
            repeat(count) { completedSpans.poll()?.let(flushed::add) }
            return flushed
        }
    }
}
