package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport

/** Renders one trace's report in whichever format was asked for. */
internal fun render(format: ReportFormat, report: StatsReport): String = when (format) {
    ReportFormat.MARKDOWN -> renderMarkdown(report)
    ReportFormat.JSON -> renderJson(report)
    ReportFormat.HTML -> renderHtml(report)
}

/** Renders a whole run's aggregate in whichever format was asked for. */
internal fun renderIterations(format: ReportFormat, report: IterationsReport): String = when (format) {
    ReportFormat.MARKDOWN -> renderIterationsMarkdown(report)
    ReportFormat.JSON -> renderIterationsJson(report)
    ReportFormat.HTML -> renderIterationsHtml(report)
}
