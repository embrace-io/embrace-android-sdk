package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * A token that represents a span.
 */
interface SpanToken {

    /**
     * Stops the span with an optional explicit end time
     */
    fun stop(endTimeMs: Long? = null)
}
