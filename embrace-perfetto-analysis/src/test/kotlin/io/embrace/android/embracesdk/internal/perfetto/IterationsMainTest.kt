package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class IterationsMainTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the run is reported before any of it is read, one line per benchmark`() {
        val text = describeIterations(
            options(ReportFormat.JSON),
            listOf(iteration(SESSION, 0), iteration(SESSION, 1), iteration(FIXTURE, 0)),
        )
        assertTrue(text, text.startsWith("perfetto iteration analysis"))
        assertTrue(text, text.contains("  dir: $DIR"))
        assertTrue(text, text.contains("  benchmark: $SESSION (2 iterations)"))
        assertTrue(text, text.contains("  benchmark: $FIXTURE (1 iteration)"))
        assertTrue(text, text.endsWith("  report: $OUTPUT (json)"))
    }

    @Test
    fun `a report is named for the directory it aggregates when nothing else names it`() {
        val options = checkNotNull(parseArgs(SPEC, arrayOf("perf/macrobenchmark/device", "--format", "json")))
        assertEquals(File("perf/macrobenchmark/device-report.json"), options.output)
        assertEquals(File("perf/macrobenchmark/device"), options.input)
    }

    @Test
    fun `an iteration names its benchmark, its index, and the trace it was read from`() {
        val trace = tmp.newFile("iter000.perfetto-trace").apply { writeText("0123456789") }
        assertEquals(
            "$SESSION iteration 3: iter000.perfetto-trace (10 bytes)",
            describeIteration(IterationTrace(SESSION, 3, trace)),
        )
    }

    private fun options(format: ReportFormat) =
        CliOptions(inputs = listOf(File(DIR)), format = format, output = File(OUTPUT))

    private fun iteration(benchmark: String, index: Int) =
        IterationTrace(benchmark, index, File(DIR, "$benchmark-$index.perfetto-trace"))

    private companion object {
        const val DIR = "perf/macrobenchmark/device"
        const val OUTPUT = "device-report.json"
        const val SESSION = "SessionBenchmark.sessionEnd"
        const val FIXTURE = "TraceFixtureBenchmark.sessionEndTraceFixture"
        val SPEC = CliSpec("analyseIterations", listOf("<dir>"))
    }
}
