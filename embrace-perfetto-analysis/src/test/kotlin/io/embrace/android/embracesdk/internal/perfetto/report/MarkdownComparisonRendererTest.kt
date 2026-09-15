package io.embrace.android.embracesdk.internal.perfetto.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class MarkdownComparisonRendererTest {

    @Test
    fun `both runs are described above the benchmarks set against each other`() {
        val text = renderComparisonMarkdown(comparisonReport())
        assertEquals(
            listOf(
                "# Perfetto run comparison",
                "",
                "- baseline: $BASELINE_RUN",
                "- candidate: $CANDIDATE_RUN",
                "- durations: microseconds",
                "- delta: the candidate less the baseline, so a positive delta is a regression",
                "- moved: whether the delta cleared the noise, which is both runs' deviations added together",
            ),
            text.lines().take(7),
        )
    }

    @Test
    fun `a benchmark names each run's iterations and what moved between them`() {
        val text = renderComparisonMarkdown(comparisonReport())
        assertTrue(text, text.contains("## $SESSION"))
        assertTrue(text, text.contains("- iterations: 2 baseline, 3 candidate"))
        assertTrue(text, text.contains("- moved: 1 slower, 0 faster of 1 sections compared"))
    }

    @Test
    fun `the columns name both runs, the difference between them, and the noise it is judged against`() {
        val text = renderComparisonMarkdown(comparisonReport())
        assertEquals(
            listOf("operation", "base", "cand", "delta", "delta%", "noise", "moved", "base iters", "cand iters"),
            row(text, 0),
        )
        assertEquals(row(text, 0).size, row(text, 1).size)
    }

    @Test
    fun `a row reports both means, the delta and whether it cleared the noise`() {
        assertEquals(
            listOf(OPERATION, "1.000", "1.500", "0.500", "50.0000", "0.250", "yes", "2", "3"),
            row(renderComparisonMarkdown(comparisonReport()), 2),
        )
    }

    @Test
    fun `a section within the noise says so rather than being left out of the table`() {
        val text = renderComparisonMarkdown(
            comparisonReport(listOf(benchmarkComparison(operations = listOf(operationComparison(moved = false))))),
        )
        assertEquals("no", row(text, 2)[6])
    }

    @Test
    fun `each compared benchmark gets its own block, rather than being pooled into one`() {
        val text = renderComparisonMarkdown(
            comparisonReport(listOf(benchmarkComparison(), benchmarkComparison(benchmark = FIXTURE))),
        )
        assertEquals(
            listOf("## $SESSION", "## $FIXTURE", "## Benchmarks only in baseline", "## Benchmarks only in candidate"),
            text.lines().filter { it.startsWith("## ") },
        )
        assertEquals(8, text.lines().count { it.startsWith("### ") })
    }

    @Test
    fun `counters are tabled as the totals they tally, with no noise to judge them against`() {
        val text =
            renderComparisonMarkdown(comparisonReport(listOf(benchmarkComparison(counters = listOf(counterComparison())))))
        assertEquals(
            listOf("counter", "base", "cand", "delta", "delta%", "base iters", "cand iters"),
            counterRow(text, 0),
        )
        assertEquals(listOf(COUNTER, "2000.0", "2500.0", "500.0", "25.0000", "2", "3"), counterRow(text, 2))
    }

    @Test
    fun `what only one run recorded is listed under the benchmark it is missing from`() {
        val text = renderComparisonMarkdown(
            comparisonReport(
                listOf(benchmarkComparison(baselineOnly = listOf("emb-gone"), candidateOnly = listOf("emb-new"))),
            ),
        )
        assertTrue(text, text.contains("### Only in baseline\n\n- emb-gone"))
        assertTrue(text, text.contains("### Only in candidate\n\n- emb-new"))
    }

    @Test
    fun `a benchmark only one run ran is named below them all, being nowhere in the tables`() {
        val text = renderComparisonMarkdown(comparisonReport(candidateOnly = listOf(FIXTURE)))
        assertTrue(text, text.contains("## Benchmarks only in baseline\n\n$EMPTY_SECTION"))
        assertEquals("- $FIXTURE", text.lines().last())
    }

    @Test
    fun `two runs with no benchmark in common say so rather than trailing off after the header`() {
        val text = renderComparisonMarkdown(comparisonReport(emptyList()))
        assertTrue(text, text.contains("- candidate: $CANDIDATE_RUN\n"))
        assertEquals(3, text.lines().count { it == EMPTY_SECTION })
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
