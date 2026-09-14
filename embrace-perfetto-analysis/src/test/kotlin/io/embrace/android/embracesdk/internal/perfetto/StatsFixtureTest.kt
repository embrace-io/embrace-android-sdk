package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Measures the committed macrobenchmark capture. Every expected value was measured from that file,
 * so regenerating it means updating them.
 */
internal class StatsFixtureTest {

    private val model = trace().let { TraceInterpreter().interpret(ftraceEvents(it), threadNames(it)) }

    @Test
    fun `a thread spans its first slice opening to its last slice closing`() {
        assertEquals(
            listOf(113_728_625L, 35_853_875L, 1_463_365_501L, 8_001_416L, 2_147_276_751L, 5_638_833L),
            model.threads.values.map(ThreadTimeline::wallSpanNanos),
        )
    }

    @Test
    fun `a section that ran on three threads is measured once per thread, not pooled across them`() {
        val stats = statsFor(REPEATED)
        assertEquals(listOf(9874, 9892, 9894), stats.map(OperationStats::tid))
        assertEquals(
            listOf("io.embrace.android.embracesdk.macrobenchmark.app", "emb-http-reques", "emb-non-io-reg"),
            stats.map(OperationStats::threadName),
        )
        assertEquals(listOf(446, 15, 40), stats.map(OperationStats::count))
        assertEquals(model.slices(REPEATED).size, stats.sumOf(OperationStats::count))
    }

    @Test
    fun `a repeated section is summarised by durations the capture recorded`() {
        val stats = statsFor(REPEATED).first()
        assertEquals(446, stats.count)
        assertEquals(2_349_381L, stats.sumNanos)
        assertEquals(541L, stats.minNanos)
        assertEquals(230_209L, stats.maxNanos)
        assertEquals(5267.670, stats.meanNanos, 0.001)
        assertEquals(12_383.513, stats.stdevNanos, 0.001)
        assertEquals(
            listOf(Percentile(50, 3_584), Percentile(90, 7_209), Percentile(95, 9_584), Percentile(99, 36_250)),
            stats.percentiles,
        )
    }

    @Test
    fun `a section that ran once reports that duration as every statistic, deviating by nothing`() {
        val stats = statsFor("emb-sdk-start").single()
        assertEquals(9874, stats.tid)
        assertEquals(1, stats.count)
        assertEquals(12_083_458L, stats.minNanos)
        assertEquals(12_083_458L, stats.maxNanos)
        assertEquals(12_083_458L, stats.sumNanos)
        assertEquals(1.2083458E7, stats.meanNanos, 0.0)
        assertEquals(0.0, stats.stdevNanos, 0.0)
        assertEquals(listOf(12_083_458L), stats.percentiles.map(Percentile::durationNanos).distinct())
    }

    @Test
    fun `the statistics of every section the capture recorded stay within their own bounds`() {
        calculateStats(model, model.names.toList()).operations.forEach { stats ->
            val percentiles = stats.percentiles.map(Percentile::durationNanos)
            assertEquals("$stats", percentiles.sorted(), percentiles)
            assertTrue("$stats", stats.minNanos <= percentiles.first() && percentiles.last() <= stats.maxNanos)
            assertTrue("$stats", stats.meanNanos in stats.minNanos.toDouble()..stats.maxNanos.toDouble())
        }
    }

    @Test
    fun `a section the capture never recorded is named as missing rather than silently dropped`() {
        val stats = calculateStats(model, listOf("emb-sdk-start", "emb-not-in-this-trace"))
        assertEquals(listOf("emb-sdk-start"), stats.operations.map(OperationStats::name))
        assertEquals(listOf("emb-not-in-this-trace"), stats.missing)
    }

    private fun statsFor(name: String) = calculateStats(model, listOf(name)).operations

    private fun trace() = parseTrace(fixture())

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
        const val REPEATED = "emb-mf-span-snapshot-changed"
    }
}
