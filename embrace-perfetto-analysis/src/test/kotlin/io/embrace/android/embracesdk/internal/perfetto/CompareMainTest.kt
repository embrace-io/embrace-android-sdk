package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.report.ReportFormat
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class CompareMainTest {

    @Test
    fun `both runs are reported before either is read, one line per benchmark`() {
        val text = describeComparison(
            options(),
            listOf(iteration(BASELINE, SESSION, 0), iteration(BASELINE, SESSION, 1)),
            listOf(iteration(CANDIDATE, SESSION, 0), iteration(CANDIDATE, FIXTURE, 0)),
        )
        assertTrue(text, text.startsWith("perfetto iteration comparison"))
        assertTrue(text, text.contains("  baseline: $BASELINE\n    benchmark: $SESSION (2 iterations)"))
        assertTrue(text, text.contains("  candidate: $CANDIDATE\n    benchmark: $SESSION (1 iteration)"))
        assertTrue(text, text.contains("    benchmark: $FIXTURE (1 iteration)"))
        assertTrue(text, text.endsWith("  report: $OUTPUT (html)"))
    }

    @Test
    fun `a comparison is named for the baseline directory when nothing else names it`() {
        val options = checkNotNull(parseArgs(SPEC, arrayOf(BASELINE, CANDIDATE, "--format", "json")))
        assertEquals(listOf(File(BASELINE), File(CANDIDATE)), options.inputs)
        assertEquals(File("perf/macrobenchmark/baseline-report.json"), options.output)
    }

    @Test
    fun `comparing takes two runs and no more`() {
        assertNull(parseArgs(SPEC, arrayOf(BASELINE)))
        assertNull(parseArgs(SPEC, arrayOf(BASELINE, CANDIDATE, "perf/macrobenchmark/third")))
    }

    @Test
    fun `a run that has been read reports what it aggregated`() {
        assertEquals(
            "  baseline: aggregated 20 iterations of 2 benchmarks",
            summariseRun("baseline", IterationsReport(BASELINE, 2, 20, emptyList())),
        )
        assertEquals(
            "  candidate: aggregated 1 iteration of 1 benchmark",
            summariseRun("candidate", IterationsReport(CANDIDATE, 1, 1, emptyList())),
        )
    }

    private fun options() = CliOptions(
        inputs = listOf(File(BASELINE), File(CANDIDATE)),
        format = ReportFormat.HTML,
        output = File(OUTPUT),
    )

    private fun iteration(dir: String, benchmark: String, index: Int) =
        IterationTrace(benchmark, index, File(dir, "$benchmark-$index.perfetto-trace"))

    private companion object {
        const val BASELINE = "perf/macrobenchmark/baseline"
        const val CANDIDATE = "perf/macrobenchmark/candidate"
        const val OUTPUT = "comparison.html"
        const val SESSION = "SessionBenchmark.sessionEnd"
        const val FIXTURE = "TraceFixtureBenchmark.sessionEndTraceFixture"
        val SPEC = CliSpec("compareIterations", listOf("<baseline-dir>", "<candidate-dir>"))
    }
}
