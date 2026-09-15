package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport

private const val TEMPLATE = "/iterations-report.html"

internal fun renderIterationsHtml(report: IterationsReport): String =
    renderPage(TEMPLATE, renderIterationsJson(report))
