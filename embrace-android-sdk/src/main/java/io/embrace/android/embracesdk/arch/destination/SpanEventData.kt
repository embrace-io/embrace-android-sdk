package io.embrace.android.embracesdk.arch.destination

/**
 * Represents a span event that can be added to the current session span.
 */
internal class SpanEventData(
    val spanName: String,
    val spanStartTimeMs: Long,
    val attributes: Map<String, String>?
)
