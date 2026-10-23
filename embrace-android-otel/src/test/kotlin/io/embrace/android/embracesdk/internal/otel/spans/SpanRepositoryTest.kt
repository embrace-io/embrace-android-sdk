package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.toEmbracePayload
import io.opentelemetry.kotlin.OpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class SpanRepositoryTest {
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var repository: SpanRepository

    @Before
    fun setup() {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        repository = SpanRepository(errorHandler)
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
        assertEquals(ErrorCodeAttribute.Failure, startedSpan.errorCode)
    }

    @Test
    fun `timed out span is failed at its deadline once the timeout elapses`() {
        val span = FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.Timeout(1000L)).apply { start(100L) }
        repository.trackStartedEmbraceSpan(span)

        // before the deadline the span is left
        repository.stopTimedOutSpans(now = 1099L)
        assertTrue(span.isRecording)
        assertNull(span.errorCode)

        // at/after the deadline the span is failed, with an end time equal to the deadline
        repository.stopTimedOutSpans(now = 1100L)
        assertFalse(span.isRecording)
        assertEquals(ErrorCodeAttribute.Failure, span.errorCode)
        assertEquals(1100L, span.spanEndTimeMs)
    }

    @Test
    fun `spans without a timeout are not affected by the sweep`() {
        val onBackground = FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.OnBackground).apply { start(0L) }
        val none = FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.None).apply { start(0L) }
        repository.trackStartedEmbraceSpan(onBackground)
        repository.trackStartedEmbraceSpan(none)

        repository.stopTimedOutSpans(now = Long.MAX_VALUE)

        assertTrue(onBackground.isRecording)
        assertTrue(none.isRecording)
    }

    @Test
    fun `already stopped timed out spans are not stopped again`() {
        val span = FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.Timeout(1000L)).apply {
            start(100L)
            stop(endTimeMs = 500L)
        }
        repository.trackStartedEmbraceSpan(span)

        repository.stopTimedOutSpans(now = Long.MAX_VALUE)

        // original end time is retained
        assertEquals(500L, span.spanEndTimeMs)
        assertNull(span.errorCode)
    }

    @Test
    fun `autoTerminate stops ON_BACKGROUND roots and all descendants with the same end time`() {
        val openTelemetry = fakeOpenTelemetry(OtelSdkMode.REGULAR)
        val autoRoot = FakeEmbraceSdkSpan(
            openTelemetry = openTelemetry,
            terminationMode = SpanTerminationMode.OnBackground,
        ).apply { start(0L) }
        val child = createChildSpan(openTelemetry, parent = autoRoot)
        val grandchild = createChildSpan(openTelemetry, parent = child)
        val stoppedChild = createChildSpan(openTelemetry, parent = autoRoot).apply { stop(endTimeMs = 50L) }
        val childOfStoppedChild = createChildSpan(openTelemetry, parent = stoppedChild)
        val nonAutoRoot = FakeEmbraceSdkSpan(openTelemetry = openTelemetry).apply { start(0L) }
        val childOfNonAutoRoot = createChildSpan(openTelemetry, parent = nonAutoRoot)
        listOf(autoRoot, child, grandchild, stoppedChild, childOfStoppedChild, nonAutoRoot, childOfNonAutoRoot).forEach {
            repository.trackStartedEmbraceSpan(it)
        }

        repository.autoTerminateEmbraceSpans(now = 1000L)

        // the whole ON_BACKGROUND tree is stopped at the same time, including descendants of an already-stopped span
        listOf(autoRoot, child, grandchild, childOfStoppedChild).forEach { span ->
            assertFalse(span.isRecording)
            assertEquals(1000L, span.spanEndTimeMs)
        }

        // a descendant that was already stopped keeps its original end time
        assertEquals(50L, stoppedChild.spanEndTimeMs)

        // a root without auto termination and its descendants are untouched
        listOf(nonAutoRoot, childOfNonAutoRoot).forEach { span ->
            assertTrue(span.isRecording)
            assertNull(span.spanEndTimeMs)
        }
    }

    @Test
    fun `a span that fails to stop does not prevent others being failed`() {
        val spans = trackWithThrowingSpan { FakeEmbraceSdkSpan().apply { start(0L) } }
        repository.failActiveEmbraceSpans(100L)
        spans.forEach { assertFalse(it.isRecording) }
        assertSpanStopFailReported()
    }

    @Test
    fun `a span that fails to stop does not prevent others timing out`() {
        val spans = trackWithThrowingSpan {
            FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.Timeout(1000L)).apply { start(0L) }
        }
        repository.stopTimedOutSpans(now = 1000L)
        spans.forEach { assertFalse(it.isRecording) }
        assertSpanStopFailReported()
    }

    @Test
    fun `a span that fails to stop does not prevent others being auto terminated`() {
        val spans = trackWithThrowingSpan {
            FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.OnBackground).apply { start(0L) }
        }
        repository.autoTerminateEmbraceSpans(now = 1000L)
        spans.forEach { assertFalse(it.isRecording) }
        assertSpanStopFailReported()
    }

    private fun assertSpanStopFailReported() {
        assertEquals(SdkErrorType.SpanStopFail.toString(), errorHandler.sdkErrorMessages.single().msg)
    }

    private fun trackWithThrowingSpan(create: () -> FakeEmbraceSdkSpan): List<FakeEmbraceSdkSpan> {
        val throwing = ThrowingEmbraceSdkSpan(create())
        repository.trackStartedEmbraceSpan(throwing)
        val spans = List(10) { create() }
        spans.forEach(repository::trackStartedEmbraceSpan)
        assertTrue(throwing.isRecording)
        return spans
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

    private fun createChildSpan(openTelemetry: OpenTelemetry, parent: FakeEmbraceSdkSpan) =
        FakeEmbraceSdkSpan(
            openTelemetry = openTelemetry,
            parentContext = parent.createContext(openTelemetry),
        ).apply { start(10L) }
}
