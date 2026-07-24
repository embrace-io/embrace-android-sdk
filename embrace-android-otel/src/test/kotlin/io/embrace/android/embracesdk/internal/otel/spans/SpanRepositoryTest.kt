package io.embrace.android.embracesdk.internal.otel.spans

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
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
}
