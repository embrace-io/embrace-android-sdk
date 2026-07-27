package io.embrace.android.embracesdk.internal.otel.spans

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.toEmbracePayload
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SpanRepositoryTest {
    private lateinit var repository: SpanRepository

    @Before
    fun setup() {
        repository = SpanRepository()
    }

    @Test
    fun `new repository not tracking any spans`() {
        assertEquals(0, repository.getActiveEmbraceSpans().size)
        assertEquals(0, repository.getCompletedEmbraceSpans().size)
    }

    @Test
    fun `started span tracked`() {
        val startedSpan = FakeEmbraceSdkSpan.started()
        repository.trackStartedEmbraceSpan(startedSpan)
        assertSame(startedSpan, checkNotNull(repository.getEmbraceSpan(checkNotNull(startedSpan.spanId))))
        assertEquals(1, repository.getActiveEmbraceSpans().size)
        assertEquals(0, repository.getCompletedEmbraceSpans().size)
    }

    @Test
    fun `completed span tracked`() {
        val completedSpan = FakeEmbraceSdkSpan.stopped()
        repository.trackStartedEmbraceSpan(completedSpan)
        assertSame(completedSpan, checkNotNull(repository.getEmbraceSpan(checkNotNull(completedSpan.spanId))))
        assertEquals(0, repository.getActiveEmbraceSpans().size)
        assertEquals(1, repository.getCompletedEmbraceSpans().size)
    }

    @Test
    fun `not started span not tracked`() {
        repository.trackStartedEmbraceSpan(FakeEmbraceSdkSpan.notStarted())
        assertEquals(0, repository.getActiveEmbraceSpans().size)
        assertEquals(0, repository.getCompletedEmbraceSpans().size)
    }

    @Test
    fun `tracked span moved to complete only if it is actually complete`() {
        val span = FakeEmbraceSdkSpan.started()
        repository.trackStartedEmbraceSpan(span)
        assertEquals(1, repository.getActiveEmbraceSpans().size)
        assertEquals(0, repository.getCompletedEmbraceSpans().size)
        span.stop()
        assertEquals(0, repository.getActiveEmbraceSpans().size)
        assertEquals(1, repository.getCompletedEmbraceSpans().size)
    }

    @Test
    fun `completed span not available after clearing but existing reference still valid`() {
        val completedSpan = FakeEmbraceSdkSpan.stopped()
        repository.trackStartedEmbraceSpan(completedSpan)
        checkNotNull(repository.getEmbraceSpan(checkNotNull(completedSpan.spanId)))
        assertEquals(1, repository.getCompletedEmbraceSpans().size)
        repository.clearCompletedEmbraceSpans()
        assertNull(repository.getEmbraceSpan(checkNotNull(completedSpan.spanId)))
        assertEquals(0, repository.getCompletedEmbraceSpans().size)
    }

    @Test
    fun `active spans become failed and complete when they are forced to fail`() {
        val startedSpan = FakeEmbraceSdkSpan.started()
        repository.trackStartedEmbraceSpan(startedSpan)

        assertSame(startedSpan, repository.getActiveEmbraceSpans().single())
        assertTrue(startedSpan.isRecording)
        assertNull(startedSpan.errorCode)

        repository.failActiveEmbraceSpans(100L)

        assertFalse(startedSpan.isRecording)
        assertEquals(ErrorCode.FAILURE, startedSpan.errorCode)
    }

    @Test
    fun `verify default completed otel span state`() {
        assertEquals(0, repository.completedOtelSpans().size)
        assertEquals(0, repository.flushOtelSpans().size)
        assertEquals(StoreDataResult.SUCCESS, repository.storeCompletedOtelSpans(listOf()))
    }

    @Test
    fun `flushing clears completed otel spans`() {
        repository.storeCompletedOtelSpans(listOf(FakeSpanData(), FakeSpanData()).map(FakeSpanData::toEmbracePayload))
        val snapshot = repository.completedOtelSpans()
        assertEquals(2, snapshot.size)

        val flushedSpans = repository.flushOtelSpans()
        assertEquals(2, flushedSpans.size)
        repeat(2) {
            assertSame(snapshot[it], flushedSpans[it])
        }
        assertEquals(0, repository.completedOtelSpans().size)
    }

    @Test
    fun `flushing does not retain previously flushed otel spans`() {
        repository.storeCompletedOtelSpans(listOf(FakeSpanData(), FakeSpanData()).map(FakeSpanData::toEmbracePayload))
        assertEquals(2, repository.flushOtelSpans().size)

        assertEquals(0, repository.flushOtelSpans().size)
        assertEquals(0, repository.completedOtelSpans().size)

        repository.storeCompletedOtelSpans(listOf(FakeSpanData()).map(FakeSpanData::toEmbracePayload))
        assertEquals(1, repository.flushOtelSpans().size)
    }

    @Test
    fun `concurrent stores and flushes neither lose nor duplicate otel spans`() {
        val totalToStore = 5_000
        val storeDoneLatch = CountDownLatch(1)
        val flushed = ArrayList<Span>(totalToStore)

        // store spans one at a time from another thread
        val producer = SingleThreadTestScheduledExecutor()
        producer.submit {
            repeat(totalToStore) { i ->
                repository.storeCompletedOtelSpans(listOf(FakeSpanData(name = "span$i")).map(FakeSpanData::toEmbracePayload))
            }
            storeDoneLatch.countDown()
        }

        // repeatedly flush on this thread while the producer is storing
        while (storeDoneLatch.count > 0L) {
            flushed += repository.flushOtelSpans()
        }
        storeDoneLatch.await(5, TimeUnit.SECONDS)

        // final flush to drain anything stored after the last in-loop flush
        flushed += repository.flushOtelSpans()

        // every stored span should have been flushed exactly once.
        assertEquals(0, repository.completedOtelSpans().size)
        assertEquals(totalToStore, flushed.size)
        val distinctNames = flushed.mapTo(HashSet()) { it.name }
        assertEquals(totalToStore, distinctNames.size)
    }
}
