package io.embrace.android.embracesdk.internal.perfetto.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class HtmlIterationsRendererTest {

    @Test
    fun `the page is one html document, carrying the json report it draws itself from`() {
        val page = renderIterationsHtml(iterationsReport())
        assertTrue(page, page.startsWith("<!doctype html>"))
        assertTrue(page, page.trimEnd().endsWith("</html>"))
        assertEquals(renderIterationsJson(iterationsReport()), embedded(page))
    }

    @Test
    fun `a section name that would close the json block is escaped rather than ending it early`() {
        val report = iterationsReport(listOf(benchmarkStats(operations = listOf(aggregateOperation("a</script><b")))))
        val page = renderIterationsHtml(report)
        assertFalse(page, page.contains("a</script><b"))
        assertTrue(page, page.contains("""a<\/script><b"""))
        assertEquals(SCRIPT_BLOCKS, occurrences(page, "</script>"))
    }

    @Test
    fun `the details section is empty of anything generated, so a later pass owns what sits there`() {
        val page = renderIterationsHtml(iterationsReport())
        val details = page.substringAfter(DETAILS_BEGIN).substringBefore(DETAILS_END)
        assertFalse(details, details.contains(OPERATION))
        assertTrue(details, details.contains("No details yet."))
        assertEquals(listOf(1, 1), listOf(occurrences(page, DETAILS_BEGIN), occurrences(page, DETAILS_END)))
    }

    @Test
    fun `the page reads the variation and the iterations behind it, rather than dropping them`() {
        val script = renderIterationsHtml(iterationsReport()).substringAfter("</script>")
        assertTrue(script, script.contains("variationPercent"))
        assertTrue(script, script.contains("meanOccurrences"))
        assertTrue(script, script.contains("report.benchmarks"))
    }

    @Test
    fun `a run with nothing in it still renders a page rather than failing`() {
        val page = renderIterationsHtml(iterationsReport(emptyList(), iterationCount = 0))
        assertTrue(page, page.contains(""""benchmarks": []"""))
        assertTrue(page, page.contains(DETAILS_BEGIN))
    }

    @Test
    fun `every benchmark of the run reaches the page, rather than only the first`() {
        val report = iterationsReport(listOf(benchmarkStats(), benchmarkStats(benchmark = FIXTURE)), iterationCount = 4)
        val page = renderIterationsHtml(report)
        assertTrue(page, page.contains(""""benchmark": "$SESSION""""))
        assertTrue(page, page.contains(""""benchmark": "$FIXTURE""""))
    }

    @Test
    fun `the stylesheet and toolkit are inlined once, and a section named after either is left as data`() {
        val report = iterationsReport(listOf(benchmarkStats(operations = listOf(aggregateOperation("{{script}}")))))
        val page = renderIterationsHtml(report)
        assertTrue(page, page.contains("color-scheme: light"))
        assertEquals(1, occurrences(page, "var REPORT_UI"))
        assertTrue(page, page.contains(""""name": "{{script}}""""))
    }

    private fun embedded(page: String) =
        page.substringAfter("""<script type="application/json" id="report">""").substringBefore("</script>")

    private fun occurrences(page: String, marker: String) = page.split(marker).size - 1

    private companion object {
        const val DETAILS_BEGIN = "<!-- DETAILS:BEGIN -->"
        const val DETAILS_END = "<!-- DETAILS:END -->"
        const val SCRIPT_BLOCKS = 2
    }
}
