package io.embrace.android.embracesdk.internal.perfetto.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ReportFormatTest {

    @Test
    fun `a run's aggregate renders in every format, rather than only the one it is usually asked for`() {
        val report = iterationsReport()
        assertTrue(renderIterations(ReportFormat.MARKDOWN, report).startsWith("# Perfetto iteration statistics"))
        assertTrue(renderIterations(ReportFormat.JSON, report).startsWith("{"))
        assertTrue(renderIterations(ReportFormat.HTML, report).startsWith("<!doctype html>"))
    }

    @Test
    fun `a flag names one format exactly, rather than any spelling of it`() {
        assertEquals(ReportFormat.MARKDOWN, ReportFormat.from("markdown"))
        assertEquals(ReportFormat.JSON, ReportFormat.from("json"))
        assertEquals(ReportFormat.HTML, ReportFormat.from("html"))
        listOf("JSON", "Json", " json", "md", "htm", "").forEach { flag -> assertNull(flag, ReportFormat.from(flag)) }
    }
}
