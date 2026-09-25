package io.embrace.android.embracesdk.internal.perfetto.stats

import io.embrace.android.embracesdk.internal.perfetto.model.ThreadTimeline
import io.embrace.android.embracesdk.internal.perfetto.model.TraceModel
import io.embrace.android.embracesdk.internal.perfetto.model.TraceSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StatsCalculatorTest {

    @Test
    fun `every statistic is measured across every occurrence`() {
        val stats = only(model(timeline(TID, runOf("op", 2, 4, 4, 4, 5, 5, 7, 9))), "op")
        assertEquals(8, stats.count)
        assertEquals(40L, stats.sumNanos)
        assertEquals(2L, stats.minNanos)
        assertEquals(9L, stats.maxNanos)
        assertEquals(5.0, stats.meanNanos, 0.0)
        assertEquals(2.0, stats.stdevNanos, 0.0)
    }

    @Test
    fun `the deviation is the population one, so a section that ran once has none`() {
        val stats = only(model(timeline(TID, listOf(slice("op", 0, 500)))), "op")
        assertEquals(1, stats.count)
        assertEquals(500L, stats.minNanos)
        assertEquals(500L, stats.maxNanos)
        assertEquals(500.0, stats.meanNanos, 0.0)
        assertEquals(0.0, stats.stdevNanos, 0.0)
        assertEquals(listOf(500L), stats.percentiles.map(Percentile::durationNanos).distinct())
    }

    @Test
    fun `occurrences of equal duration deviate by nothing`() {
        val stats = only(model(timeline(TID, runOf("op", 100, 100, 100))), "op")
        assertEquals(0.0, stats.stdevNanos, 0.0)
        assertEquals(stats.minNanos, stats.maxNanos)
        assertEquals(stats.minNanos.toDouble(), stats.meanNanos, 0.0)
    }

    @Test
    fun `percentiles report a duration the trace recorded rather than one between two it did`() {
        val stats = only(model(timeline(TID, runOf("op", 2, 4, 4, 4, 5, 5, 7, 9))), "op")
        assertEquals(listOf(Percentile(50, 4), Percentile(90, 9), Percentile(95, 9), Percentile(99, 9)), stats.percentiles)
    }

    @Test
    fun `every section is measured at the default percentiles, in a fixed order a report can lay out`() {
        val stats = only(model(timeline(TID, runOf("op", 1, 2, 3, 4))), "op")
        assertEquals(DEFAULT_PERCENTILES, stats.percentiles.map(Percentile::rank))
    }

    @Test
    fun `occurrences pool across threads, so a section that ran on two yields one result`() {
        val model = model(
            timeline(TID, runOf("op", 10, 10)),
            timeline(OTHER_TID, runOf("op", 30, tid = OTHER_TID)),
        )
        val stats = calculateStats(model, listOf("op"), WINDOW, START).operations.single()
        assertEquals(3, stats.count)
        assertEquals(50L, stats.sumNanos)
        assertEquals(10L, stats.minNanos)
        assertEquals(30L, stats.maxNanos)
    }

    @Test
    fun `a section that took no time is still counted, rather than filtered out as noise`() {
        val stats = only(model(timeline(TID, listOf(slice("op", 0, 0)))), "op")
        assertEquals(1, stats.count)
        assertEquals(0L, stats.sumNanos)
        assertEquals(0.0, stats.stdevNanos, 0.0)
    }

    @Test
    fun `the share of wall time is measured against the whole trace window`() {
        val stats = only(model(timeline(TID, runOf("op", 100, 150))), "op", window = 1000)
        assertEquals(250L, stats.sumNanos)
        assertEquals(25.0, stats.traceWindowPercent, 0.0)
    }

    @Test
    fun `a trace with no window to measure against reports no share rather than a number that is not one`() {
        val stats = only(model(timeline(TID, runOf("op", 100))), "op", window = 0)
        assertEquals(0.0, stats.traceWindowPercent, 0.0)
        assertFalse("$stats", stats.traceWindowPercent.isNaN())
    }

    @Test
    fun `both threads count against one denominator, so their work pools into a single share`() {
        val model = model(
            timeline(TID, runOf("op", 100)),
            timeline(
                OTHER_TID,
                listOf(slice("op", 5000, 5100, OTHER_TID), slice("other", 9000, 9900, OTHER_TID)),
            ),
        )
        val stats = calculateStats(model, listOf("op"), WINDOW, START).operations.single()
        assertEquals(200L, stats.sumNanos)
        assertEquals(2.0, stats.traceWindowPercent, 0.0)
        assertEquals(listOf(100L, 4900L), model.threads.values.map(ThreadTimeline::wallSpanNanos))
    }

    @Test
    fun `a section nested inside itself is counted in both, reading past 100 rather than being clamped`() {
        val inner = slice("op", 100, 900)
        val outer = TraceSlice("op", TID, 0, 1000, 0, listOf(inner))
        val stats = only(model(timeline(TID, listOf(outer, inner))), "op", window = 1000)
        assertEquals(1800L, stats.sumNanos)
        assertEquals(180.0, stats.traceWindowPercent, 0.0)
    }

    @Test
    fun `a section the trace never recorded is named as missing rather than reported as an empty row`() {
        val stats = calculateStats(model(timeline(TID, runOf("op", 10))), listOf("op", "absent"), WINDOW, START)
        assertEquals(listOf("op"), stats.operations.map(OperationStats::name))
        assertEquals(listOf("absent"), stats.missing)
    }

    @Test
    fun `a section only one thread ran is reported once, and is not missing`() {
        val model = model(timeline(TID, runOf("op", 10)), timeline(OTHER_TID, runOf("other", 10, tid = OTHER_TID)))
        val stats = calculateStats(model, listOf("op"), WINDOW, START)
        assertEquals(listOf("op"), stats.operations.map(OperationStats::name))
        assertTrue(stats.missing.toString(), stats.missing.isEmpty())
    }

    @Test
    fun `a section named twice is measured once`() {
        val stats = calculateStats(model(timeline(TID, runOf("op", 10))), listOf("op", "op"), WINDOW, START)
        assertEquals(1, stats.operations.size)
    }

    @Test
    fun `results keep the order the sections were asked in`() {
        val model = model(
            timeline(TID, runOf("b", 1) + slice("a", 10, 11)),
            timeline(OTHER_TID, runOf("b", 1, tid = OTHER_TID) + slice("a", 10, 11, OTHER_TID)),
        )
        val stats = calculateStats(model, listOf("b", "a"), WINDOW, START).operations
        assertEquals(listOf("b", "a"), stats.map(OperationStats::name))
    }

    @Test
    fun `asking for nothing measures nothing and misses nothing`() {
        val stats = calculateStats(model(timeline(TID, runOf("op", 10))), emptyList(), WINDOW, START)
        assertEquals(TraceStats(emptyList(), emptyList(), emptyList()), stats)
    }

    private fun only(model: TraceModel, name: String, window: Long = WINDOW) =
        calculateStats(model, listOf(name), window, START).operations.single()

    private fun model(vararg timelines: ThreadTimeline) =
        TraceModel(timelines.associateBy(ThreadTimeline::tid), emptyList(), 0, 0, 0)

    private fun timeline(tid: Int, slices: List<TraceSlice>, name: String? = null) = ThreadTimeline(tid, name, slices)

    /** Lays [durations] end to end from zero, so the thread's span is their total. */
    private fun runOf(name: String, vararg durations: Long, tid: Int = TID): List<TraceSlice> {
        var start = 0L
        return durations.map { duration ->
            slice(name, start, start + duration, tid).also { start += duration }
        }
    }

    private fun slice(name: String, startNanos: Long, endNanos: Long, tid: Int = TID) =
        TraceSlice(name, tid, startNanos, endNanos, 0, emptyList())

    private companion object {
        const val TID = 9874
        const val OTHER_TID = 9891
        const val WINDOW = 10_000L
        const val START = 0L
    }
}
