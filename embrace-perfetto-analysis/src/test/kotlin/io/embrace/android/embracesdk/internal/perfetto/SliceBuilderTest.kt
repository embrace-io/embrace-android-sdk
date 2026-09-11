package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Test

internal class SliceBuilderTest {

    @Test
    fun `nested begin and end events become slices`() {
        val trace = buildSlices(listOf(begin(1000, "outer"), begin(1100, "inner"), end(1200), end(1500)))

        assertEquals(
            listOf(
                TraceSlice("outer", TID, 1000, 1500, 0),
                TraceSlice("inner", TID, 1100, 1200, 1),
            ),
            trace.slices,
        )
    }

    @Test
    fun `each thread has its own stack`() {
        val trace = buildSlices(
            listOf(
                begin(1000, "on-main", tid = TID),
                begin(1100, "on-worker", tid = OTHER_TID),
                end(1200, tid = TID),
                end(1300, tid = OTHER_TID),
            ),
        )

        // interleaved on the timeline, but neither is nested inside the other
        assertEquals(listOf(0, 0), trace.slices.map(TraceSlice::depth))
        assertEquals(listOf(TID, OTHER_TID), trace.slices.map(TraceSlice::tid))
    }

    @Test
    fun `events are stacked in timestamp order rather than arrival order`() {
        // ftrace batches per CPU, so a bundle boundary can hand these over out of order
        val trace = buildSlices(listOf(begin(1100, "inner"), end(1200), begin(1000, "outer"), end(1500)))

        assertEquals(listOf("outer", "inner"), trace.slices.map(TraceSlice::name))
        assertEquals(listOf(0, 1), trace.slices.map(TraceSlice::depth))
    }

    @Test
    fun `slices are ordered by start, then thread, then depth`() {
        val trace = buildSlices(
            listOf(
                begin(1000, "worker", tid = OTHER_TID),
                begin(1000, "main-outer", tid = TID),
                begin(1000, "main-inner", tid = TID),
                end(1100, tid = TID),
                end(1200, tid = TID),
                end(1300, tid = OTHER_TID),
            ),
        )

        assertEquals(listOf("main-outer", "main-inner", "worker"), trace.slices.map(TraceSlice::name))
    }

    @Test
    fun `payloads are read for both end forms, and for names that contain a separator or are empty`() {
        val trace = buildSlices(
            listOf(
                AtraceEvent(TID, 1000, "B|$TID|has|separator\n"),
                AtraceEvent(TID, 1100, "E|$TID\n"),
                AtraceEvent(TID, 1200, "B|$TID|\n"),
                AtraceEvent(TID, 1300, "E\n"),
                AtraceEvent(TID, 1400, "B|$TID\n"),
                AtraceEvent(TID, 1500, "E\n"),
            ),
        )

        assertEquals(listOf("has|separator", "", ""), trace.slices.map(TraceSlice::name))
    }

    @Test
    fun `events that cannot be paired are counted rather than guessed at`() {
        val trace = buildSlices(
            listOf(
                end(1000),
                begin(1100, "never-ends"),
                begin(1200, "ends"),
                end(1300),
                AtraceEvent(TID, 1400, "S|$TID|async-name|42\n"),
                AtraceEvent(TID, 1500, "C|$TID|counter|7\n"),
                AtraceEvent(TID, 1600, ""),
            ),
        )

        // the open slice is dropped rather than given an invented end
        assertEquals(listOf("ends"), trace.slices.map(TraceSlice::name))
        assertEquals(1, trace.unmatchedEndCount)
        assertEquals(1, trace.unclosedBeginCount)
        assertEquals(3, trace.ignoredEventCount)
    }

    @Test
    fun `an empty trace has no bounds and no anomalies`() {
        val trace = buildSlices(emptyList())

        assertEquals(null, trace.startNanos)
        assertEquals(null, trace.endNanos)
        assertEquals(null, trace.durationNanos)
        assertEquals(false, trace.hasAnomalies)
    }

    private fun begin(timestampNanos: Long, name: String, tid: Int = TID) =
        AtraceEvent(tid, timestampNanos, "B|$tid|$name\n")

    private fun end(timestampNanos: Long, tid: Int = TID) = AtraceEvent(tid, timestampNanos, "E|$tid\n")

    private companion object {
        const val TID = 9874
        const val OTHER_TID = 9892
    }
}
