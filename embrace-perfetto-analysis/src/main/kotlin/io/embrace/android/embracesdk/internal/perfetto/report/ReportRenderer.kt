package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport

/** Renders a report in whichever format was asked for. */
internal fun render(format: ReportFormat, report: StatsReport): String = when (format) {
    ReportFormat.MARKDOWN -> renderMarkdown(report)
    ReportFormat.JSON -> renderJson(report)
    ReportFormat.HTML -> renderHtml(report)
}
