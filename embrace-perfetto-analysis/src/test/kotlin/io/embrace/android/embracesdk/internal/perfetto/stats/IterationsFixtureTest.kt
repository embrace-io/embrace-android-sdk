package io.embrace.android.embracesdk.internal.perfetto.stats

import io.embrace.android.embracesdk.internal.perfetto.report.REPORT_JSON
import io.embrace.android.embracesdk.internal.perfetto.report.renderIterationsJson
import io.embrace.android.embracesdk.internal.perfetto.report.renderIterationsMarkdown
import io.embrace.android.embracesdk.internal.perfetto.statsReport
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class IterationsFixtureTest {

    private val report = aggregateIterations(RUN, mapOf(SESSION to listOf(iteration(), iteration())))
    private val benchmark = report.benchmarks.single()

    @Test
    fun `a run is every benchmark it ran and every iteration of each`() {
        assertEquals(RUN, report.runPath)
        assertEquals(1, report.benchmarkCount)
        assertEquals(2, report.iterationCount)
        assertEquals(listOf(0, 1), benchmark.iterations.map(IterationSummary::index))
        assertEquals(listOf(87, 87), benchmark.iterations.map(IterationSummary::sectionCount))
        assertEquals(listOf(2_536_707_168L, 2_536_707_168L), benchmark.iterations.map(IterationSummary::traceWindowNanos))
    }

    @Test
    fun `a section the whole run recorded is measured once per iteration, threads pooled`() {
        val startup = benchmark.operations.single { it.name == STARTUP }
        assertEquals(2, startup.iterations)
        assertEquals(2, startup.occurrences)
        assertEquals(listOf(IterationValue(0, 17_266_417), IterationValue(1, 17_266_417)), startup.values)
        assertEquals(17_266_417.0, startup.meanNanos, 0.0)
        assertEquals(0.0, startup.stdevNanos, 0.0)
        assertEquals(0.0, startup.variationPercent, 0.0)
    }

    @Test
    fun `two iterations of the same capture agree, so nothing is partial and nothing is missing`() {
        assertEquals(emptyList<String>(), benchmark.partial)
        assertEquals(emptyList<String>(), benchmark.missing)
        assertEquals(87, benchmark.operations.size)
    }

    @Test
    fun `a section no iteration recorded is missing, rather than absent from the report`() {
        val asked = aggregateIterations(RUN, mapOf(SESSION to listOf(iteration(listOf(STARTUP, ABSENT)))))
        val stats = asked.benchmarks.single()
        assertEquals(listOf(STARTUP), stats.operations.map(AggregateOperationStats::name))
        assertEquals(listOf(ABSENT), stats.missing)
    }

    @Test
    fun `the markdown report opens on the costliest section of the run`() {
        val rows = renderIterationsMarkdown(report).lines()
            .dropWhile { it != "### Operations" }
            .takeWhile { it != "### Counters" }
            .filter { it.startsWith("|") }
            .drop(2)
        assertEquals(87, rows.size)
        assertTrue(rows.first(), rows.first().startsWith("| $COSTLIEST | 2 | 13.0 | 0.8323 | 21113.459 |"))
        val means = benchmark.operations.map(AggregateOperationStats::meanNanos)
        assertEquals(means.sortedDescending(), means)
    }

    @Test
    fun `the json is a contract, so it reads back as the report it was rendered from`() {
        assertEquals(report, Json.decodeFromString<IterationsReport>(renderIterationsJson(report)))
        assertEquals(report, REPORT_JSON.decodeFromString<IterationsReport>(renderIterationsJson(report)))
    }

    private fun iteration(operations: List<String> = emptyList()) =
        statsReport(fixture(), parseTrace(fixture()), operations)

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
        const val RUN = "perf/macrobenchmark/device"
        const val SESSION = "SessionBenchmark.sessionEnd"
        const val STARTUP = "emb-sdk-start"
        const val COSTLIEST = "emb-mf-write-span-snapshots"
        const val ABSENT = "emb-not-in-this-trace"
    }
}
