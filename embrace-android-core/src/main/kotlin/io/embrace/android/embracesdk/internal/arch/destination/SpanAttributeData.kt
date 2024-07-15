package io.embrace.android.embracesdk.internal.arch.destination

/**
 * Represents a span attribute that can be added to the current session span.
 */
public data class SpanAttributeData(
    val key: String,
    val value: String
)
