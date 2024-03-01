package io.embrace.android.embracesdk.arch.destination

/**
 * Holds the information required to start a span.
 */
internal class StartSpanData(
    val spanName: String,
    val spanStartTimeMs: Long,
    val attributes: Map<String, String>?
)
