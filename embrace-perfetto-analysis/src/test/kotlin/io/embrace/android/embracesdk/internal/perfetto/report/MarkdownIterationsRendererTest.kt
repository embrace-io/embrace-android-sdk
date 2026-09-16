package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.DEFAULT_PERCENTILES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class MarkdownIterationsRendererTest {

    @Test
    fun `the run is described above the benchmarks it aggregates`() {
        val text = renderIterationsMarkdown(iterationsReport())
        assertEquals(
            listOf(
                "# Perfetto iteration statistics",
                "",
                "- run: $RUN",
                "- benchmarks: 1, iterations: 2",
                "- durations: microseconds",
                "- statistics: one observation per iteration, each an iteration's total for that section",
            ),
            text.lines().take(6),
        )
    }

    @Test
    fun `a benchmark names its iterations and the mean window its wall share is taken against`() {
        val text = renderIterationsMarkdown(iterationsReport())
        assertTrue(text, text.contains("## $SESSION"))
        assertTrue(text, text.contains("- iterations: 2"))
        assertTrue(text, text.contains("- trace window: mean 1200.000, the span every wall% is a share of"))
    }

    @Test
    fun `the columns name the statistics, the percentile ones following the ranks the report holds`() {
        val text = renderIterationsMarkdown(iterationsReport())
        assertEquals(
            listOf("operation", "iters", "occ/iter", "wall%", "mean", "stdev", "cv%", "min") +
                DEFAULT_PERCENTILES.map { "p$it" } + "max",
            row(text, 0),
        )
        assertEquals(row(text, 0).size, row(text, 1).size)
    }

    @Test
    fun `a row reports a section's per-iteration statistics, in the order the columns name them`() {
        assertEquals(
            listOf(OPERATION, "2", "1.0", "0.2500", "1.500", "0.500", "33.3333", "1.000") +
                List(DEFAULT_PERCENTILES.size) { "2.000" } + "2.000",
            row(renderIterationsMarkdown(iterationsReport()), 2),
        )
    }

    @Test
    fun `each benchmark of a run gets its own block, rather than being pooled into one`() {
        val text = renderIterationsMarkdown(
            iterationsReport(listOf(benchmarkStats(), benchmarkStats(benchmark = FIXTURE)), iterationCount = 4),
        )
        assertEquals(listOf("## $SESSION", "## $FIXTURE"), text.lines().filter { it.startsWith("## ") })
        assertEquals(8, text.lines().count { it.startsWith("### ") })
        assertTrue(text, text.contains("- benchmarks: 2, iterations: 4"))
    }

    @Test
    fun `counters are tabled per iteration, as the integers they tally rather than durations`() {
        val text =
            renderIterationsMarkdown(iterationsReport(listOf(benchmarkStats(counters = listOf(aggregateCounter())))))
        assertEquals(
            listOf("counter", "iters", "samples/iter", "mean", "min", "max", "sum"),
            counterRow(text, 0),
        )
        assertEquals(counterRow(text, 0).size, counterRow(text, 1).size)
        assertEquals(listOf(COUNTER, "2", "2.0", "2560.0", "1024", "4096", "5120"), counterRow(text, 2))
    }

    @Test
    fun `a section only some iterations recorded is listed apart from one none of them did`() {
        val text = renderIterationsMarkdown(
            iterationsReport(listOf(benchmarkStats(partial = listOf("emb-sometimes"), missing = listOf("absent")))),
        )
        assertTrue(text, text.contains("- emb-sometimes (recorded by some iterations, not all)"))
        assertEquals("- absent", text.lines().last())
    }

    @Test
    fun `the headings and their placeholders stay put when a benchmark recorded nothing`() {
        val text = renderIterationsMarkdown(
            iterationsReport(listOf(benchmarkStats(iterations = emptyList(), operations = emptyList()))),
        )
        assertEquals(
            listOf("### Operations", "### Counters", "### Partial", "### Missing"),
            text.lines().filter { it.startsWith("### ") },
        )
        assertEquals(4, text.lines().count { it == EMPTY_SECTION })
        assertTrue(text, text.contains("- trace window: mean 0.000,"))
    }

    @Test
    fun `a run that aggregated no benchmarks says so rather than trailing off after its header`() {
        val text = renderIterationsMarkdown(iterationsReport(emptyList(), iterationCount = 0))
        assertEquals(EMPTY_SECTION, text.lines().last())
        assertTrue(text, text.contains("- benchmarks: 0, iterations: 0"))
    }

    private fun row(text: String, index: Int): List<String> {
        val lines = text.lines()
        return cells(lines.subList(lines.indexOf("### Operations") + 2, lines.size)[index])
    }

    private fun counterRow(text: String, index: Int): List<String> {
        val lines = text.lines()
        return cells(lines.drop(lines.indexOf("### Counters")).filter { it.startsWith("|") }[index])
    }

    private fun cells(row: String) = row.removeSurrounding("| ", " |").split(" | ")
}
