package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import kotlinx.serialization.encodeToString

internal fun renderJson(report: StatsReport): String = REPORT_JSON.encodeToString(report)
