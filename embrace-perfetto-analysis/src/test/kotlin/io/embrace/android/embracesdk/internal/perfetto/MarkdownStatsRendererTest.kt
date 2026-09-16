package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class MarkdownStatsRendererTest {

    @Test
    fun `the capture is described above a table that names its columns`() {
        val text = renderMarkdown(report())
        assertEquals(
            listOf(
                "# Perfetto trace statistics",
                "",
                "- trace: t.perfetto.gz (2048 bytes)",
                "- recorded: 12 slices of 3 distinct sections across 2 threads",
                "- durations: microseconds",
                "- trace window: 1200.000, the span every wall% is a share of",
            ),
            text.lines().take(6),
        )
        assertEquals(
            listOf("operation", "thread", "tid", "count", "total", "wall%", "mean", "stdev", "min") +
                DEFAULT_PERCENTILES.map { "p$it" } + "max",
            row(text, 0),
        )
        assertEquals(row(text, 0).size, row(text, 1).size)
    }

    @Test
    fun `a row reports the microseconds of every statistic, in the order the columns name them`() {
        assertEquals(
            listOf("op", "main", "9874", "2", "3.000", "0.2500", "1.500", "0.500", "1.000") +
                List(DEFAULT_PERCENTILES.size) { "2.000" } + "2.000",
            row(renderMarkdown(report()), 2),
        )
    }

    @Test
    fun `a section that ran on two threads is one row for each, rather than pooled into one`() {
        val text = renderMarkdown(report(listOf(operation(), operation(tid = 9892, threadName = "emb-io-reg"))))
        assertEquals(listOf("main", "emb-io-reg"), listOf(row(text, 2)[1], row(text, 3)[1]))
        assertEquals(listOf("9874", "9892"), listOf(row(text, 2)[2], row(text, 3)[2]))
    }

    @Test
    fun `a thread the trace named nothing for leaves the tid to identify it`() {
        assertEquals("-", row(renderMarkdown(report(listOf(operation(threadName = null)))), 2)[1])
    }

    @Test
    fun `a pipe in a section name is escaped, rather than ending the column early`() {
        val text = renderMarkdown(report(listOf(operation(name = "a|b"))))
        assertTrue(text, text.contains("| a\\|b | main |"))
    }

    @Test
    fun `counters are tabled under their own heading, as the integers they tally rather than durations`() {
        val text = renderMarkdown(report(counters = listOf(counter())))
        assertEquals(
            listOf("counter", "tid", "samples", "first", "last", "max", "total"),
            counterRow(text, 0),
        )
        assertEquals(counterRow(text, 0).size, counterRow(text, 1).size)
        assertEquals(
            listOf("emb-mf-bytes-written", "6951", "37", "1024", "40960", "98304", "139264"),
            counterRow(text, 2),
        )
    }

    @Test
    fun `a counter two threads published names each of them, since they share the one tally`() {
        val text = renderMarkdown(report(counters = listOf(counter(tids = listOf(6951, 6952)))))
        assertEquals("6951,6952", counterRow(text, 2)[1])
    }

    @Test
    fun `sections that were asked for but never recorded are listed under their own heading`() {
        val text = renderMarkdown(report(missing = listOf("absent", "also-absent")))
        assertEquals(listOf("- absent", "- also-absent"), text.lines().takeLast(2))
    }

    @Test
    fun `the headings and their placeholders stay put when the report holds nothing`() {
        val text = renderMarkdown(report(emptyList()))
        assertEquals(
            listOf("# Perfetto trace statistics", "## Operations", "## Counters", "## Missing"),
            text.lines().filter { it.startsWith("#") },
        )
        assertEquals(3, text.lines().count { it == "_none_" })
    }

    private fun row(text: String, index: Int): List<String> {
        val lines = text.lines()
        val table = lines.subList(lines.indexOf("## Operations") + 2, lines.size)
        return cells(table[index])
    }

    private fun counterRow(text: String, index: Int): List<String> {
        val lines = text.lines()
        return cells(lines.drop(lines.indexOf("## Counters")).filter { it.startsWith("|") }[index])
    }

    private fun cells(row: String) = row.removeSurrounding("| ", " |").split(" | ")

    private fun report(
        operations: List<OperationStats> = listOf(operation()),
        missing: List<String> = emptyList(),
        counters: List<CounterStats> = emptyList(),
    ) = StatsReport("t.perfetto.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(operations, missing, counters))

    private fun counter(
        name: String = "emb-mf-bytes-written",
        tids: List<Int> = listOf(6951),
    ) = CounterStats(
        name = name,
        tids = tids,
        sampleCount = 37,
        firstValue = 1024,
        lastValue = 40960,
        maxValue = 98304,
        total = 139264,
        readings = emptyList(),
    )

    private fun operation(
        name: String = "op",
        tid: Int = 9874,
        threadName: String? = "main",
    ) = OperationStats(
        name = name,
        tid = tid,
        threadName = threadName,
        count = 2,
        sumNanos = 3000,
        traceWindowPercent = 0.25,
        minNanos = 1000,
        maxNanos = 2000,
        meanNanos = 1500.0,
        stdevNanos = 500.0,
        percentiles = DEFAULT_PERCENTILES.map { Percentile(it, 2000) },
    )
}
