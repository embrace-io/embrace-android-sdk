package io.embrace.android.embracesdk.internal.perfetto.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ReportFormatTest {

    @Test
    fun `a flag names one format exactly, rather than any spelling of it`() {
        assertEquals(ReportFormat.MARKDOWN, ReportFormat.from("markdown"))
        assertEquals(ReportFormat.JSON, ReportFormat.from("json"))
        assertEquals(ReportFormat.HTML, ReportFormat.from("html"))
        listOf("JSON", "Json", " json", "md", "htm", "").forEach { flag -> assertNull(flag, ReportFormat.from(flag)) }
    }
}
