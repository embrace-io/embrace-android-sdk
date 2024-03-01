package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.Severity

/**
 * Represents a Log event that can be added to the current session span.
 */
internal class LogEventData(
    val startTimeMs: Long,
    val severity: Severity,
    val message: String,
    val attributes: Map<String, String>?
)
