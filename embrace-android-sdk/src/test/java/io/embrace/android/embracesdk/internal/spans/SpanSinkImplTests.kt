package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.mockk.every
import io.mockk.spyk
import io.mockk.unmockkObject
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class SpanSinkImplTests {
    private lateinit var spanSink: SpanSink

    @Before
    fun setup() {
        spanSink = spyk(SpanSinkImpl())
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, spanSink.completedSpans().size)
        assertEquals(0, spanSink.flushSpans().size)
        assertEquals(CompletableResultCode.ofSuccess(), spanSink.storeCompletedSpans(listOf()))
    }

    @Test
    fun `flushing clears completed spans`() {
        spanSink.storeCompletedSpans(listOf(FakeSpanData(), FakeSpanData()))
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
    fun `flushing does not block writing and does not clear the spans added before the flush determines what to flush`() {
        spanSink.storeCompletedSpans(listOf(FakeSpanData(name = "fake1"), FakeSpanData(name = "fake2")))
        val unblockCompletedSpansLatch = CountDownLatch(1)
        val unblockFlushLatch = CountDownLatch(1)
        val checkLock = CountDownLatch(1)
        val flushedCount = AtomicInteger(-1)

        // Artificially block the completedSpans() - and thus flushSpans() - from completing
        every { spanSink.completedSpans() } answers {
            val spans = callOriginal()
            unblockFlushLatch.countDown()
            unblockCompletedSpansLatch.await(1, TimeUnit.SECONDS)
            spans
        }

        // Produces this order of operations:
        // 1. thread1 flushes spanSink and is about to return 2 spans but execution is paused
        // 2. thread2 adds a new span to spanSink and then unblocks thread1
        // 3. thread1 will return 2 spans despite spanSink already containing the extra span added by thread2
        // 4. thread1 will clear the two spans that it has flushed and returns, unblocking the check
        // 5. spanSink should have 1 span in it after the flush only removing the spans that it has flushed

        val thread1 = SingleThreadTestScheduledExecutor()
        thread1.submit {
            val flushedSpans = spanSink.flushSpans()
            flushedCount.set(flushedSpans.size)
            checkLock.countDown()
        }

        unblockFlushLatch.await(1, TimeUnit.SECONDS)
        val thread2 = SingleThreadTestScheduledExecutor()
        thread2.submit {
            spanSink.storeCompletedSpans(listOf(FakeSpanData(name = "fake3")))
            unblockCompletedSpansLatch.countDown()
        }

        checkLock.await(1, TimeUnit.SECONDS)
        assertEquals(2, flushedCount.get())

        unmockkObject(spanSink)
        assertEquals(1, spanSink.completedSpans().size)
    }
}
