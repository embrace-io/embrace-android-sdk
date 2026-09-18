package io.embrace.android.embracesdk.internal.perfetto.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class HtmlComparisonRendererTest {

    @Test
    fun `the page is one html document, carrying the json comparison it draws itself from`() {
        val page = renderComparisonHtml(comparisonReport())
        assertTrue(page, page.startsWith("<!doctype html>"))
        assertTrue(page, page.trimEnd().endsWith("</html>"))
        assertEquals(renderComparisonJson(comparisonReport()), embedded(page))
    }

    @Test
    fun `a section name that would close the json block is escaped rather than ending it early`() {
        val report = comparisonReport(
            listOf(benchmarkComparison(operations = listOf(operationComparison("a</script><b")))),
        )
        val page = renderComparisonHtml(report)
        assertFalse(page, page.contains("a</script><b"))
        assertTrue(page, page.contains("""a<\/script><b"""))
        assertEquals(SCRIPT_BLOCKS, occurrences(page, "</script>"))
    }

    @Test
    fun `the details section is empty of anything generated, so a later pass owns what sits there`() {
        val page = renderComparisonHtml(comparisonReport())
        val details = page.substringAfter(DETAILS_BEGIN).substringBefore(DETAILS_END)
        assertFalse(details, details.contains(OPERATION))
        assertTrue(details, details.contains("No details yet."))
        assertEquals(listOf(1, 1), listOf(occurrences(page, DETAILS_BEGIN), occurrences(page, DETAILS_END)))
    }

    @Test
    fun `the page reads the noise and whether a section cleared it, not just the delta`() {
        val script = renderComparisonHtml(comparisonReport()).substringAfter("</script>")
        assertTrue(script, script.contains("noiseNanos"))
        assertTrue(script, script.contains("deltaPercent"))
        assertTrue(script, script.contains("moved"))
        assertTrue(script, script.contains("report.benchmarks"))
    }

    @Test
    fun `a comparison of nothing still renders a page rather than failing`() {
        val page = renderComparisonHtml(comparisonReport(emptyList()))
        assertTrue(page, page.contains(""""benchmarks": []"""))
        assertTrue(page, page.contains(DETAILS_BEGIN))
    }

    @Test
    fun `every compared benchmark reaches the page, as do the ones only one run ran`() {
        val report = comparisonReport(
            benchmarks = listOf(benchmarkComparison(), benchmarkComparison(benchmark = FIXTURE)),
            candidateOnly = listOf(UNPAIRED),
        )
        val page = renderComparisonHtml(report)
        assertTrue(page, page.contains(""""benchmark": "$SESSION""""))
        assertTrue(page, page.contains(""""benchmark": "$FIXTURE""""))
        assertTrue(page, page.contains(""""$UNPAIRED""""))
    }

    @Test
    fun `the stylesheet and toolkit are inlined once, and a section named after either is left as data`() {
        val report = comparisonReport(
            listOf(benchmarkComparison(operations = listOf(operationComparison("{{script}}")))),
        )
        val page = renderComparisonHtml(report)
        assertTrue(page, page.contains("color-scheme: light"))
        assertEquals(1, occurrences(page, "var REPORT_UI"))
        assertTrue(page, page.contains(""""name": "{{script}}""""))
    }

    @Test
    fun `a bar says which way a section went, since a chart of deltas is drawn by magnitude`() {
        val page = renderComparisonHtml(comparisonReport())
        assertTrue(page, page.contains(".bar.slower"))
        assertTrue(page, page.contains(".bar.faster"))
        assertTrue(page, page.substringAfter("</script>").contains("'slower'"))
    }

    private fun embedded(page: String) =
        page.substringAfter("""<script type="application/json" id="report">""").substringBefore("</script>")

    private fun occurrences(page: String, marker: String) = page.split(marker).size - 1

    private companion object {
        const val DETAILS_BEGIN = "<!-- DETAILS:BEGIN -->"
        const val DETAILS_END = "<!-- DETAILS:END -->"
        const val SCRIPT_BLOCKS = 2
        const val UNPAIRED = "StartupBenchmark.coldStart"
    }
}
