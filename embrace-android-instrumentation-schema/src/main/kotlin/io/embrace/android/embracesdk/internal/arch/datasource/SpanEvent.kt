package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * An event that occurred during a span
 */
interface SpanEvent {
    val name: String
    val timestampNanos: Long
    val attributes: Map<String, String>
}
