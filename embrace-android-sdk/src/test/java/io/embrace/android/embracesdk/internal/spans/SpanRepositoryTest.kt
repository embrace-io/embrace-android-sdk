package io.embrace.android.embracesdk.internal.spans

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SpanRepositoryTest {
    private lateinit var repository: SpanRepository

    @Before
    fun setup() {
        repository = SpanRepository()
    }

    @Test
    fun `new repository not tracking any spans`() {
        assertEquals(0, repository.getActiveSpans().size)
        assertEquals(0, repository.getCompletedSpans().size)
    }

    @Test
    fun `started span tracked`() {
        val startedSpan = FakePersistableEmbraceSpan.started()
        repository.trackStartedSpan(startedSpan)
        assertSame(startedSpan, checkNotNull(repository.getSpan(checkNotNull(startedSpan.spanId))))
        assertEquals(1, repository.getActiveSpans().size)
        assertEquals(0, repository.getCompletedSpans().size)
    }

    @Test
    fun `completed span tracked`() {
        val completedSpan = FakePersistableEmbraceSpan.stopped()
        repository.trackStartedSpan(completedSpan)
        assertSame(completedSpan, checkNotNull(repository.getSpan(checkNotNull(completedSpan.spanId))))
        assertEquals(0, repository.getActiveSpans().size)
        assertEquals(1, repository.getCompletedSpans().size)
    }

    @Test
    fun `not started span not tracked`() {
        repository.trackStartedSpan(FakePersistableEmbraceSpan.notStarted())
        assertEquals(0, repository.getActiveSpans().size)
        assertEquals(0, repository.getCompletedSpans().size)
    }

    @Test
    fun `tracked span moved to complete only if it is actually complete`() {
        val span = FakePersistableEmbraceSpan.started()
        val spanId = checkNotNull(span.spanId)
        repository.trackStartedSpan(span)
        repository.trackedSpanStopped(spanId)
        assertEquals(1, repository.getActiveSpans().size)
        assertEquals(0, repository.getCompletedSpans().size)
        span.stop()
        assertEquals(1, repository.getActiveSpans().size)
        assertEquals(0, repository.getCompletedSpans().size)
        repository.trackedSpanStopped(spanId)
        assertEquals(0, repository.getActiveSpans().size)
        assertEquals(1, repository.getCompletedSpans().size)
    }

    @Test
    fun `spans not tracked twice`() {
        val span = FakePersistableEmbraceSpan.started()
        val spanId = checkNotNull(span.spanId)
        repository.trackStartedSpan(span)
        repository.trackStartedSpan(span)
        assertEquals(1, repository.getActiveSpans().size)
        assertEquals(0, repository.getCompletedSpans().size)
        span.stop()
        repository.trackedSpanStopped(spanId)
        repository.trackedSpanStopped(spanId)
        assertEquals(0, repository.getActiveSpans().size)
        assertEquals(1, repository.getCompletedSpans().size)
    }

    @Test
    fun `completed span not available after clearing but existing reference still valid`() {
        val completedSpan = FakePersistableEmbraceSpan.stopped()
        repository.trackStartedSpan(completedSpan)
        checkNotNull(repository.getSpan(checkNotNull(completedSpan.spanId)))
        assertEquals(1, repository.getCompletedSpans().size)
        repository.clearCompletedSpans()
        assertNull(repository.getSpan(checkNotNull(completedSpan.spanId)))
        assertEquals(0, repository.getCompletedSpans().size)
    }

    @Test
    fun `active spans become failed and complete when they are forced to fail`() {
        val startedSpan = FakePersistableEmbraceSpan.started()
        repository.trackStartedSpan(startedSpan)

        assertSame(startedSpan, repository.getActiveSpans().single())
        assertTrue(startedSpan.isRecording)
        assertNull(startedSpan.errorCode)

        repository.failActiveSpans(100L)

        assertFalse(startedSpan.isRecording)
        assertEquals(ErrorCode.FAILURE, startedSpan.errorCode)
    }
}
