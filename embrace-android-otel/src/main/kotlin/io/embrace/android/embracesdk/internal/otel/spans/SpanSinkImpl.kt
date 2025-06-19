package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceSpanData
import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

class SpanSinkImpl : SpanSink {
    private val completedSpans: Queue<EmbraceSpanData> = ConcurrentLinkedQueue()
    private val spansToFlush = AtomicReference<List<EmbraceSpanData>>(listOf())

    override fun storeCompletedSpans(spans: List<OtelJavaSpanData>): OtelJavaCompletableResultCode {
        try {
            completedSpans += spans.map { it.toEmbraceSpanData() }
        } catch (t: Throwable) {
            return OtelJavaCompletableResultCode.ofFailure()
        }

        return OtelJavaCompletableResultCode.ofSuccess()
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
