package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.CounterReading
import io.embrace.android.embracesdk.internal.perfetto.stats.CounterStats
import io.embrace.android.embracesdk.internal.perfetto.stats.DEFAULT_PERCENTILES
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationStats
import io.embrace.android.embracesdk.internal.perfetto.stats.Percentile
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.TraceStats
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
        val page = renderHtml(report(emptyList(), emptyList()))
        assertTrue(page, page.contains(""""operations": []"""))
        assertTrue(page, page.contains(""""counters": []"""))
        assertTrue(page, page.contains(DETAILS_BEGIN))
    }

    @Test
    fun `the page reads every counter the report carries, rather than dropping them`() {
        val page = renderHtml(report(counters = listOf(counter())))
        val script = page.substringAfter("</script>")
        assertTrue(script, script.contains("report.stats.counters"))
        assertTrue(page, page.contains(""""name": "$COUNTER""""))
    }

    @Test
    fun `the page draws itself with the shared stylesheet and toolkit inlined, asking for neither over the network`() {
        val page = renderHtml(report())
        assertTrue(page, page.contains("var REPORT_UI"))
        assertTrue(page, page.contains("color-scheme: light"))
        assertFalse(page, page.contains("{{"))
    }

    private fun embedded(page: String) =
        page.substringAfter("""<script type="application/json" id="report">""").substringBefore("</script>")

    private fun occurrences(page: String, marker: String) = page.split(marker).size - 1

    private fun report(
        operations: List<OperationStats> = listOf(operation()),
        counters: List<CounterStats> = listOf(counter()),
    ) = StatsReport("t.perfetto.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(operations, listOf("absent"), counters))

    private fun counter(name: String = COUNTER) = CounterStats(
        name = name,
        tids = listOf(9874),
        sampleCount = 2,
        firstValue = 1024,
        lastValue = 4096,
        maxValue = 4096,
        total = 4096,
        readings = listOf(CounterReading(9874, 0, 1024), CounterReading(9874, 500, 4096)),
    )

    private fun operation(name: String = OPERATION) = OperationStats(
        name = name,
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
        const val COUNTER = "emb-sf-bytes-written"
        const val DETAILS_BEGIN = "<!-- DETAILS:BEGIN -->"
        const val DETAILS_END = "<!-- DETAILS:END -->"
        const val SCRIPT_BLOCKS = 2
    }
}
