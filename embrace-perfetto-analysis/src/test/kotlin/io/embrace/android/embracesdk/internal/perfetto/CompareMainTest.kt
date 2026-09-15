package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.report.ReportFormat
import io.embrace.android.embracesdk.internal.perfetto.stats.BenchmarkComparison
import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationComparison
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
    fun `a comparison is named for both runs, so it does not write over either one's own report`() {
        val options = checkNotNull(parseArgs(SPEC, arrayOf(BASELINE, CANDIDATE, "--format", "json")))
        assertEquals(listOf(File(BASELINE), File(CANDIDATE)), options.inputs)
        assertEquals(File("perf/macrobenchmark/baseline-vs-candidate-report.json"), options.output)
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

    @Test
    fun `the comparison is summarised one line per benchmark, naming only what one run has`() {
        val text = summariseComparison(
            ComparisonReport(
                baselinePath = BASELINE,
                candidatePath = CANDIDATE,
                benchmarks = listOf(
                    comparison(SESSION, sections = 14, slower = 3, faster = 2, candidateOnly = listOf("emb-new")),
                    comparison(FIXTURE, sections = 1, slower = 0, faster = 0),
                ),
                baselineOnly = emptyList(),
                candidateOnly = listOf(EXTRA),
            ),
        )
        assertEquals(
            listOf(
                "  $SESSION: 14 sections compared, 3 slower, 2 faster, 1 only in candidate",
                "  $FIXTURE: 1 section compared, 0 slower, 0 faster",
                "  $EXTRA: only in candidate, so nothing to compare it with",
            ),
            text.lines(),
        )
    }

    @Test
    fun `two runs with no benchmark in common say so rather than summarising nothing`() {
        val text =
            summariseComparison(ComparisonReport(BASELINE, CANDIDATE, emptyList(), listOf(SESSION), listOf(EXTRA)))
        assertTrue(text, text.contains("  $SESSION: only in baseline"))
        assertTrue(text, text.contains("  $EXTRA: only in candidate"))
    }

    private fun comparison(
        benchmark: String,
        sections: Int,
        slower: Int,
        faster: Int,
        candidateOnly: List<String> = emptyList(),
    ) = BenchmarkComparison(
        benchmark = benchmark,
        baselineIterations = 10,
        candidateIterations = 10,
        slower = slower,
        faster = faster,
        operations = List(sections) { operationComparison() },
        counters = emptyList(),
        baselineOnly = emptyList(),
        candidateOnly = candidateOnly,
    )

    private fun operationComparison() = OperationComparison(
        name = "emb-sdk-start",
        baselineMeanNanos = 1000.0,
        candidateMeanNanos = 1000.0,
        deltaNanos = 0.0,
        deltaPercent = 0.0,
        baselineStdevNanos = 0.0,
        candidateStdevNanos = 0.0,
        noiseNanos = 0.0,
        moved = false,
        baselineIterations = 10,
        candidateIterations = 10,
    )

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
        const val EXTRA = "StartupBenchmark.coldStart"
        val SPEC = CliSpec("compareIterations", listOf("<baseline-dir>", "<candidate-dir>"))
    }
}
