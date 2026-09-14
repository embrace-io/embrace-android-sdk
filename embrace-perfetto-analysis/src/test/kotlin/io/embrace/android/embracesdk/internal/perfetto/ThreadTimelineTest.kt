package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ThreadTimelineTest {

    @Test
    fun `a thread that recorded nothing spans no time, rather than failing to say`() {
        assertEquals(0L, ThreadTimeline(TID, null, emptyList()).wallSpanNanos)
    }

    @Test
    fun `one section spans its own duration`() {
        assertEquals(100L, ThreadTimeline(TID, null, listOf(slice("only", 900, 1000))).wallSpanNanos)
    }

    @Test
    fun `the span counts the gaps between sections, since atrace says nothing about them`() {
        val timeline = ThreadTimeline(TID, null, listOf(slice("first", 0, 10), slice("second", 990, 1000)))
        assertEquals(1000L, timeline.wallSpanNanos)
    }

    @Test
    fun `the span runs to the latest end, which is not the end of the section that started last`() {
        val inner = slice("inner", 1, 2)
        val outer = TraceSlice("outer", TID, 0, 100, 0, listOf(inner))
        assertEquals(100L, ThreadTimeline(TID, null, listOf(outer, inner)).wallSpanNanos)
    }

    private fun slice(name: String, startNanos: Long, endNanos: Long) =
        TraceSlice(name, TID, startNanos, endNanos, 0, emptyList())

    private companion object {
        const val TID = 9874
    }
}
