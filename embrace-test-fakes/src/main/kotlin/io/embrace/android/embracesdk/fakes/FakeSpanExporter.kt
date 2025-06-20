package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

class FakeSpanExporter : OtelJavaSpanExporter {
    val exportedSpans: MutableList<OtelJavaSpanData> = mutableListOf()

    private val latches = mutableListOf<CountDownLatch>()
    private var totalExported = 0

    override fun export(spans: MutableCollection<OtelJavaSpanData>): OtelJavaCompletableResultCode {
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
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    override fun flush(): OtelJavaCompletableResultCode {
        synchronized(exportedSpans) {
            exportedSpans.clear()
        }
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    override fun shutdown(): OtelJavaCompletableResultCode {
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    /**
     * Block the thread until the number of spans expected have been exported by this exporter since the last flush
     */
    fun awaitSpanExport(count: Int = 1, timeout: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        val latch = synchronized(exportedSpans) {
            return@synchronized if (count > totalExported) {
                val newLatch = CountDownLatch(count - totalExported)
                latches.add(newLatch)
                newLatch
            } else {
                null
            }
        }

        return latch?.await(timeout, unit) ?: true
    }
}
