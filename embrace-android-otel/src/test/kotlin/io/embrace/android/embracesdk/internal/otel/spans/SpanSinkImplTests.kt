package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class SpanSinkImplTests {
    private lateinit var spanSink: SpanSink

    @Before
    fun setup() {
        spanSink = SpanSinkImpl()
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, spanSink.completedSpans().size)
        assertEquals(0, spanSink.flushSpans().size)
        assertEquals(StoreDataResult.SUCCESS, spanSink.storeCompletedSpans(listOf()))
    }

    @Test
    fun `flushing clears completed spans`() {
        spanSink.storeCompletedSpans(listOf(FakeSpanData(), FakeSpanData()).map(FakeSpanData::toEmbraceSpanData))
        val snapshot = spanSink.completedSpans()
        assertEquals(2, snapshot.size)

        val flushedSpans = spanSink.flushSpans()
        assertEquals(2, flushedSpans.size)
        repeat(2) {
            assertSame(snapshot[it], flushedSpans[it])
        }
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `flushing does not retain previously flushed spans`() {
        spanSink.storeCompletedSpans(listOf(FakeSpanData(), FakeSpanData()).map(FakeSpanData::toEmbraceSpanData))
        assertEquals(2, spanSink.flushSpans().size)

        assertEquals(0, spanSink.flushSpans().size)
        assertEquals(0, spanSink.completedSpans().size)

        spanSink.storeCompletedSpans(listOf(FakeSpanData()).map(FakeSpanData::toEmbraceSpanData))
        assertEquals(1, spanSink.flushSpans().size)
    }

    @Test
    fun `concurrent stores and flushes neither lose nor duplicate spans`() {
        val totalToStore = 5_000
        val storeDoneLatch = CountDownLatch(1)
        val flushed = ArrayList<EmbraceSpanData>(totalToStore)

        // store spans one at a time from another thread
        val producer = SingleThreadTestScheduledExecutor()
        producer.submit {
            repeat(totalToStore) { i ->
                spanSink.storeCompletedSpans(listOf(FakeSpanData(name = "span$i")).map(FakeSpanData::toEmbraceSpanData))
            }
            storeDoneLatch.countDown()
        }

        // repeatedly flush on this thread while the producer is storing
        while (storeDoneLatch.count > 0L) {
            flushed += spanSink.flushSpans()
        }
        storeDoneLatch.await(5, TimeUnit.SECONDS)

        // final flush to drain anything stored after the last in-loop flush
        flushed += spanSink.flushSpans()

        // every stored span should have been flushed exactly once.
        assertEquals(0, spanSink.completedSpans().size)
        assertEquals(totalToStore, flushed.size)
        val distinctNames = flushed.mapTo(HashSet()) { it.name }
        assertEquals(totalToStore, distinctNames.size)
    }
}
