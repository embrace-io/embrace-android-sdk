package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Represents a span attribute that can be added to the current session span.
 */
@InternalApi
public data class SpanAttributeData(
    val key: String,
    val value: String
)
