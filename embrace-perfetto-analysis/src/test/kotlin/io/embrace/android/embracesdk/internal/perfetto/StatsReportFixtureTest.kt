package io.embrace.android.embracesdk.internal.perfetto

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
        assertEquals(86, report.sectionCount)
        assertEquals(6, report.threadCount)
        assertEquals(listOf(ABSENT), report.stats.missing)
    }

    @Test
    fun `a section that ran once renders as one markdown row of microseconds`() {
        val ran = "12083.458"
        assertEquals(
            listOf(STARTUP, MAIN_THREAD, "9874", "1") +
                listOf(ran, ran, "0.000", ran) +
                List(DEFAULT_PERCENTILES.size) { ran } + ran,
            cells(dataRows(renderMarkdown(report(listOf(STARTUP)))).single()),
        )
    }

    @Test
    fun `a section that ran on three threads renders a row for each, thread named and tid ascending`() {
        val rows = dataRows(renderMarkdown(report(listOf(REPEATED)))).map(::cells)
        assertEquals(listOf(MAIN_THREAD, "emb-http-reques", "emb-non-io-reg"), rows.map { it[1] })
        assertEquals(listOf("9874", "9892", "9894"), rows.map { it[2] })
        assertEquals(listOf("446", "15", "40"), rows.map { it[3] })
        assertEquals(
            listOf("2349.381", "5.268", "12.384", "0.541", "3.584", "7.209", "9.584", "36.250", "230.209"),
            rows.first().drop(4),
        )
    }

    @Test
    fun `json carries the nanoseconds themselves, so nothing is rounded for a machine`() {
        val text = renderJson(report(listOf(STARTUP)))
        assertTrue(text, text.contains(""""sumNanos": 12083458"""))
        assertTrue(text, text.contains(""""threadName": "$MAIN_THREAD""""))
    }

    @Test
    fun `asking for every section reports each one in name order, with a row per thread`() {
        val report = statsReport(options(allOperations = true), trace)
        val names = report.stats.operations.map(OperationStats::name).distinct()
        assertEquals(names.sorted(), names)
        assertEquals(86, names.size)
        assertEquals(report.stats.operations.size, dataRows(renderMarkdown(report)).size)
    }

    private fun report(operations: List<String>) = statsReport(options(operations = operations), trace)

    private fun options(operations: List<String> = emptyList(), allOperations: Boolean = false) =
        Options(fixture(), operations = operations, allOperations = allOperations)

    private fun cells(row: String) = row.removeSurrounding("| ", " |").split(" | ")

    private fun dataRows(text: String): List<String> {
        val lines = text.lines()
        return lines.subList(lines.indexOf("## Operations") + 4, lines.indexOf("## Missing") - 1)
    }

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
        const val MAIN_THREAD = "io.embrace.android.embracesdk.macrobenchmark.app"
        const val STARTUP = "emb-sdk-start"
        const val REPEATED = "emb-mf-span-snapshot-changed"
        const val ABSENT = "emb-not-in-this-trace"
    }
}
