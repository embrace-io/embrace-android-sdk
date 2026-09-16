package io.embrace.android.embracesdk.internal.perfetto.stats

import io.embrace.android.embracesdk.internal.perfetto.model.ThreadTimeline
import io.embrace.android.embracesdk.internal.perfetto.model.TraceInterpreter
import io.embrace.android.embracesdk.internal.perfetto.trace.ftraceEvents
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import io.embrace.android.embracesdk.internal.perfetto.trace.printEvents
import io.embrace.android.embracesdk.internal.perfetto.trace.threadNames
import io.embrace.android.embracesdk.internal.perfetto.trace.traceStartNanos
import io.embrace.android.embracesdk.internal.perfetto.trace.traceWindowNanos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Measures the committed macrobenchmark capture. Every expected value was measured from that file,
 * so regenerating it means updating them.
 */
internal class StatsFixtureTest {

    private val trace = parseTrace(fixture())
    private val events = ftraceEvents(trace)
    private val model = TraceInterpreter().interpret(events, threadNames(trace))
    private val window = traceWindowNanos(events)
    private val start = traceStartNanos(events)

    @Test
    fun `a thread spans its first slice opening to its last slice closing`() {
        assertEquals(
            listOf(148_400_959L, 63_548_625L, 1_980_180_042L, 8_953_042L, 2_476_584_376L, 6_738_291L),
            model.threads.values.map(ThreadTimeline::wallSpanNanos),
        )
    }

    @Test
    fun `the capture spans its first ftrace event to its last, far wider than any one thread`() {
        assertEquals(2_536_707_168L, window)
        assertEquals(events.size, printEvents(events).size)
        assertTrue("$window", model.threads.values.all { it.wallSpanNanos < window })
    }

    @Test
    fun `a section that ran on three threads is pooled into one result, not measured per thread`() {
        val stats = statsFor(REPEATED).single()
        assertEquals(501, stats.count)
        assertEquals(model.slices(REPEATED).size, stats.count)
        assertEquals(0.11604, round(stats.traceWindowPercent), 0.0)
    }

    @Test
    fun `a repeated section is summarised by durations the capture recorded`() {
        val stats = statsFor(REPEATED).single()
        assertEquals(501, stats.count)
        assertEquals(2_943_592L, stats.sumNanos)
        assertEquals(458L, stats.minNanos)
        assertEquals(165_083L, stats.maxNanos)
        assertEquals(5875.433, stats.meanNanos, 0.001)
        assertEquals(15114.260, stats.stdevNanos, 0.001)
        assertEquals(
            listOf(Percentile(50, 3_500), Percentile(90, 6_917), Percentile(95, 9_792), Percentile(99, 88_125)),
            stats.percentiles,
        )
    }

    @Test
    fun `a section that ran once reports that duration as every statistic, deviating by nothing`() {
        val stats = statsFor("emb-sdk-start").single()
        assertEquals(1, stats.count)
        assertEquals(17_266_417L, stats.minNanos)
        assertEquals(17_266_417L, stats.maxNanos)
        assertEquals(17_266_417L, stats.sumNanos)
        assertEquals(1.7266417E7, stats.meanNanos, 0.0)
        assertEquals(0.680663, round(stats.traceWindowPercent), 0.0)
        assertEquals(0.0, stats.stdevNanos, 0.0)
        assertEquals(listOf(17_266_417L), stats.percentiles.map(Percentile::durationNanos).distinct())
    }

    @Test
    fun `the statistics of every section the capture recorded stay within their own bounds`() {
        calculateStats(model, model.names.toList(), window, start).operations.forEach { stats ->
            val percentiles = stats.percentiles.map(Percentile::durationNanos)
            assertEquals("$stats", percentiles.sorted(), percentiles)
            assertTrue("$stats", stats.minNanos <= percentiles.first() && percentiles.last() <= stats.maxNanos)
            assertTrue("$stats", stats.meanNanos in stats.minNanos.toDouble()..stats.maxNanos.toDouble())
            assertTrue("$stats", stats.traceWindowPercent in 0.0..100.0)
        }
    }

    @Test
    fun `a section the capture never recorded is named as missing rather than silently dropped`() {
        val stats = calculateStats(model, listOf("emb-sdk-start", "emb-not-in-this-trace"), window, start)
        assertEquals(listOf("emb-sdk-start"), stats.operations.map(OperationStats::name))
        assertEquals(listOf("emb-not-in-this-trace"), stats.missing)
    }

    private fun statsFor(name: String) = calculateStats(model, listOf(name), window, start).operations

    private fun round(percent: Double) = BigDecimal(percent).setScale(6, RoundingMode.HALF_UP).toDouble()

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
        const val REPEATED = "emb-mf-span-snapshot-changed"
    }
}
