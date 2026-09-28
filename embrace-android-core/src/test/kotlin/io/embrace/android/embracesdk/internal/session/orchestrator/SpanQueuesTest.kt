package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanQueuesTest {

    @Test
    fun `a completed span queue never compacts`() {
        val queue = completedSpansQueue()
        val other = Span(spanId = "other", name = "other")
        val first = Span(spanId = "span", name = "first")
        val second = Span(spanId = "span", name = "second")

        queue.add(listOf(first, other, second))
        assertEquals(listOf(first, other, second), queue.drain())
    }

    @Test
    fun `a span snapshot queue identifies spans by their span ID`() {
        val queue = spanSnapshotsQueue()
        val span = startedSpan()
        val other = startedSpan()

        queue.add(listOf(span, other, span))
        assertEquals(listOf(other, span), queue.drain())
    }

    private fun startedSpan() = FakeEmbraceSdkSpan().apply { start(FakeClock().now()) }
}
