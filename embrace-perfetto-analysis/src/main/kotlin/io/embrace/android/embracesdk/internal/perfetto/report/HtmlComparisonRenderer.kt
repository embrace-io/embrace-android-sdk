package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport

private const val TEMPLATE = "/comparison-report.html"

internal fun renderComparisonHtml(report: ComparisonReport): String =
    renderPage(TEMPLATE, renderComparisonJson(report))
