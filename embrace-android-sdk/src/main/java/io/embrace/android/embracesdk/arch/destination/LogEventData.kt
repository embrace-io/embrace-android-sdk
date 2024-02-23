package io.embrace.android.embracesdk.arch.destination

import io.opentelemetry.api.logs.Severity

/**
 * Represents a Log event that can be added to the current session span.
 */
internal class LogEventData(
    val timestampNanos: Long,
    val severity: Severity,
    val severityText: String? = null,
    val message: String,
    val attributes: Map<String, String>?
)
