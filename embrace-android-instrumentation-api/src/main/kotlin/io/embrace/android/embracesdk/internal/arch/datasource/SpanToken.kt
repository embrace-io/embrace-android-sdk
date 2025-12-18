package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute

/**
 * A token that represents a span.
 */
interface SpanToken {

    /**
     * Stops the span with an optional explicit end time
     */
    fun stop(endTimeMs: Long? = null, errorCode: ErrorCodeAttribute? = null)

    /**
     * Returns true if the span is currently recording
     */
    fun isRecording(): Boolean

    /**
     * Adds an attribute to the span
     */
    fun addAttribute(key: String, value: String)

    /**
     * Gets the start time in ms or null if the span has not started
     */
    fun getStartTimeMs(): Long?

    /**
     * Set the value of the attribute with the given key, overwriting the original value if it's already set
     */
    fun setSystemAttribute(key: String, value: String)

    /**
     * Add an event to the span
     */
    fun addEvent(name: String, eventTimeMs: Long, attributes: Map<String, String>)
}
