package io.embrace.android.embracesdk.arch.destination

/**
 * Represents a Log event that can be added to the current session span.
 */
internal class LogEventData(
    val startTimeNanos: Long,
    val severityNumber: Int,
    val severityText: String?,
    val message: String,
    val attributes: Map<String, String>?
)
