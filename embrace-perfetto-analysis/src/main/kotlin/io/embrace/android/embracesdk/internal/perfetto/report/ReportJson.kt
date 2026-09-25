package io.embrace.android.embracesdk.internal.perfetto.report

import kotlinx.serialization.json.Json

/**
 * How every json report is written. Pretty-printed because a report is read by people as often as by
 * `compareIterations`.
 */
internal val REPORT_JSON: Json = Json { prettyPrint = true }
