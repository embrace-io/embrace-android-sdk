package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport
import kotlinx.serialization.encodeToString

internal fun renderComparisonJson(report: ComparisonReport): String = REPORT_JSON.encodeToString(report)
