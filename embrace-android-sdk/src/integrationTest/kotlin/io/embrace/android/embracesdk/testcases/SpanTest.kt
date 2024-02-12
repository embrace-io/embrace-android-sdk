package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class SpanTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `SDK can receive a SpanExporter`() {
        with(testRule) {
            val fakeSpanExporter = FakeSpanExporter()
            embrace.addSpanExporter(fakeSpanExporter)
            embrace.start(harness.fakeCoreModule.context)
            fakeSpanExporter.awaitSpanExport()
            assertTrue(
                fakeSpanExporter.exportedSpans.map { it.name }.containsAll(
                    listOf("emb-sdk-init")
                )
            )
            assertTrue(
                fakeSpanExporter.exportedSpans.all { spanData ->
                    spanData.attributes.asMap().map { it.key.key }.contains("emb.sequence_id")
                }
            )
        }
    }
}

internal class FakeSpanExporter : SpanExporter {
    val exportedSpans = mutableListOf<SpanData>()
    private val latches = mutableListOf<CountDownLatch>()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        synchronized(exportedSpans) {
            exportedSpans.addAll(spans)
            latches.forEach { latch ->
                repeat(min(latch.count.toInt(), spans.size)) {
                    latch.countDown()
                }
            }
            latches.removeIf { it.count == 0L }
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
        return if (count <= exportedSpans.size) {
            true
        } else {
            synchronized(exportedSpans) {
                val latch = CountDownLatch(count)
                latches.add(latch)
                latch
            }.await(timeout, unit)
        }
    }

}
