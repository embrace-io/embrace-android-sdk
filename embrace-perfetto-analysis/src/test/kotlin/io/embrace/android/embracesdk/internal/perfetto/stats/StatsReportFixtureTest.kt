package io.embrace.android.embracesdk.internal.perfetto.stats

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.report.renderJson
import io.embrace.android.embracesdk.internal.perfetto.report.renderMarkdown
import io.embrace.android.embracesdk.internal.perfetto.statsReport
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class StatsReportFixtureTest {

    private val trace = parseTrace(fixture())

    @Test
    fun `the report describes the capture it measured, whichever sections were asked for`() {
        val report = report(listOf(STARTUP, ABSENT))
        assertEquals(1094, report.sliceCount)
        assertEquals(87, report.sectionCount)
        assertEquals(6, report.threadCount)
        assertEquals(2_536_707_168L, report.traceWindowNanos)
        assertEquals(listOf(ABSENT), report.stats.missing)
    }

    @Test
    fun `a section that ran once renders as one markdown row of microseconds`() {
        val ran = "17266.417"
        assertEquals(
            listOf(STARTUP, "1") +
                listOf(ran, "0.6807", ran, "0.000", ran) +
                List(DEFAULT_PERCENTILES.size) { ran } + ran,
            cells(dataRows(renderMarkdown(report(listOf(STARTUP)))).single()),
        )
    }

    @Test
    fun `a section that ran on three threads renders a single pooled row`() {
        val row = dataRows(renderMarkdown(report(listOf(REPEATED)))).map(::cells).single()
        assertEquals("501", row[1])
        assertEquals("0.1160", row[3])
        assertEquals(
            listOf("2943.592", "5.875", "15.114", "0.458", "3.500", "6.917", "9.792", "88.125", "165.083"),
            listOf(row[2]) + row.drop(4),
        )
    }

    @Test
    fun `json carries the nanoseconds themselves, so nothing is rounded for a machine`() {
        val text = renderJson(report(listOf(STARTUP)))
        assertTrue(text, text.contains(""""sumNanos": 17266417"""))
        assertTrue(text, text.contains(""""count": 1"""))
    }

    @Test
    fun `asking for every section reports each one once, in name order`() {
        val report = statsReport(options(), trace)
        val names = report.stats.operations.map(OperationStats::name)
        assertEquals(names.distinct(), names)
        assertEquals(names.sorted(), names)
        assertEquals(87, names.size)
        assertEquals(report.stats.operations.size, dataRows(renderMarkdown(report)).size)
    }

    @Test
    fun `every counter the capture recorded is reported, whether or not a section was asked for`() {
        val counters = report(listOf(STARTUP)).stats.counters
        assertEquals(
            listOf(
                "emb-mf-bytes-written",
                "emb-mf-files-written",
                "emb-sf-bytes-serialized",
                "emb-sf-bytes-written",
                "emb-sf-files-written",
            ),
            counters.map(CounterStats::name),
        )
        assertEquals(77, counters.sumOf(CounterStats::sampleCount))
    }

    @Test
    fun `a counter that restarted with the next session part totals both runs, not just the last`() {
        val files = report(listOf(STARTUP)).stats.counters.single { it.name == "emb-mf-files-written" }
        assertEquals(listOf(6951), files.tids)
        assertEquals(37, files.sampleCount)
        assertEquals(listOf(1L, 13L, 24L), listOf(files.firstValue, files.lastValue, files.maxValue))
        assertEquals(37L, files.total)
    }

    @Test
    fun `a counter sampled once reports that value as everything it counted`() {
        val serialized = report(listOf(STARTUP)).stats.counters.single { it.name == "emb-sf-bytes-serialized" }
        assertEquals(1, serialized.sampleCount)
        assertEquals(listOf(38_632L, 38_632L), listOf(serialized.total, serialized.maxValue))
    }

    @Test
    fun `the markdown counters table renders a row for each, in the order the report holds them`() {
        val rows = counterRows(renderMarkdown(report(listOf(STARTUP))))
        assertEquals(5, rows.size)
        assertEquals(
            listOf("emb-mf-files-written", "6951", "37", "1", "13", "24", "37"),
            rows[1],
        )
    }

    private fun counterRows(text: String): List<List<String>> {
        val lines = text.lines()
        return lines.drop(lines.indexOf("## Counters"))
            .filter { it.startsWith("|") }
            .drop(2)
            .map(::cells)
    }

    private fun report(operations: List<String>) = statsReport(options(operations = operations), trace)

    private fun options(operations: List<String> = emptyList()) =
        CliOptions(inputs = listOf(fixture()), operations = operations, output = File("report.md"))

    private fun cells(row: String) = row.removeSurrounding("| ", " |").split(" | ")

    private fun dataRows(text: String): List<String> {
        val lines = text.lines()
        return lines.subList(lines.indexOf("## Operations") + 4, lines.indexOf("## Counters") - 1)
    }

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
        const val STARTUP = "emb-sdk-start"
        const val REPEATED = "emb-mf-span-snapshot-changed"
        const val ABSENT = "emb-not-in-this-trace"
    }
}
