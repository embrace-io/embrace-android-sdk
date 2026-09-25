package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import kotlinx.serialization.encodeToString

internal fun renderIterationsJson(report: IterationsReport): String = REPORT_JSON.encodeToString(report)
