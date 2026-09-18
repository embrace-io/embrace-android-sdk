package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport

private const val TEMPLATE = "/report.html"

internal fun renderHtml(report: StatsReport): String = renderPage(TEMPLATE, renderJson(report))
