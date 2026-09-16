package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { prettyPrint = true }

internal fun renderJson(report: StatsReport): String = json.encodeToString(report)
