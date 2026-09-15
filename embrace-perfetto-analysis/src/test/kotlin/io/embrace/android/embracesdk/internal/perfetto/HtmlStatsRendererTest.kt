package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class HtmlStatsRendererTest {

    @Test
    fun `the page is one html document, carrying the json report it draws itself from`() {
        val page = renderHtml(report())
        assertTrue(page, page.startsWith("<!doctype html>"))
        assertTrue(page, page.trimEnd().endsWith("</html>"))
        assertEquals(renderJson(report()), embedded(page))
    }

    @Test
    fun `a section name that would close the json block is escaped rather than ending it early`() {
        val page = renderHtml(report(listOf(operation(name = "a</script><b"))))
        assertFalse(page, page.contains("a</script><b"))
        assertTrue(page, page.contains("""a<\/script><b"""))
        assertEquals(SCRIPT_BLOCKS, occurrences(page, "</script>"))
    }

    @Test
    fun `the details section is empty of anything generated, so a later pass owns what sits there`() {
        val page = renderHtml(report())
        val details = page.substringAfter(DETAILS_BEGIN).substringBefore(DETAILS_END)
        assertFalse(details, details.contains(OPERATION))
        assertTrue(details, details.contains("No details yet."))
        assertEquals(listOf(1, 1), listOf(occurrences(page, DETAILS_BEGIN), occurrences(page, DETAILS_END)))
    }

    @Test
    fun `the page reads the wall clock share and the window it is a share of, rather than dropping them`() {
        val script = renderHtml(report()).substringAfter("</script>")
        assertTrue(script, script.contains("traceWindowPercent"))
        assertTrue(script, script.contains("traceWindowNanos"))
    }

    @Test
    fun `a report with nothing in it still renders a page rather than failing`() {
        val page = renderHtml(report(emptyList()))
        assertTrue(page, page.contains(""""operations": []"""))
        assertTrue(page, page.contains(DETAILS_BEGIN))
    }

    private fun embedded(page: String) =
        page.substringAfter("""<script type="application/json" id="report">""").substringBefore("</script>")

    private fun occurrences(page: String, marker: String) = page.split(marker).size - 1

    private fun report(operations: List<OperationStats> = listOf(operation())) =
        StatsReport("t.perfetto.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(operations, listOf("absent")))

    private fun operation(name: String = OPERATION) = OperationStats(
        name = name,
        tid = 9874,
        threadName = "main",
        count = 2,
        sumNanos = 3000,
        traceWindowPercent = 0.25,
        minNanos = 1000,
        maxNanos = 2000,
        meanNanos = 1500.0,
        stdevNanos = 500.0,
        percentiles = DEFAULT_PERCENTILES.map { Percentile(it, 2000) },
    )

    private companion object {
        const val OPERATION = "emb-sdk-start"
        const val DETAILS_BEGIN = "<!-- DETAILS:BEGIN -->"
        const val DETAILS_END = "<!-- DETAILS:END -->"
        const val SCRIPT_BLOCKS = 2
    }
}
