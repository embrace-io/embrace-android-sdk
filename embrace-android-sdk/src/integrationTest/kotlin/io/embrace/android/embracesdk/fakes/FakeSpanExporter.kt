package io.embrace.android.embracesdk.fakes

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

internal class FakeSpanExporter : SpanExporter {
    val exportedSpans = mutableListOf<SpanData>()

    private val latches = mutableListOf<CountDownLatch>()
    private var totalExported = 0

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        synchronized(exportedSpans) {
            exportedSpans.addAll(spans)
            latches.forEach { latch ->
                repeat(min(latch.count.toInt(), spans.size)) {
                    latch.countDown()
                }
            }
            latches.removeIf { it.count == 0L }
            totalExported += spans.size
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        synchronized(exportedSpans) {
            exportedSpans.clear()
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    /**
     * Block the thread until the number of spans expected have been exported by this exporter since the last flush
     */
    fun awaitSpanExport(count: Int = 1, timeout: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        val latch = synchronized(exportedSpans) {
            return@synchronized if (count > totalExported) {
                val newLatch = CountDownLatch(count)
                latches.add(newLatch)
                newLatch
            } else {
                null
            }
        }

        return latch?.await(timeout, unit) ?: true
    }
}
