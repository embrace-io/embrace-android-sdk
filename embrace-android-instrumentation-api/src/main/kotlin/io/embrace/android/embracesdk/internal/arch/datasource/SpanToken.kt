package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
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
     * Returns true if this object represents a valid span instance
     */
    fun isValid(): Boolean

    /**
     * Adds an attribute to the span
     */
    fun addAttribute(key: String, value: String)

    /**
     * Gets the start time in ms or null if the span has not started
     */
    fun getStartTimeMs(): Long?

    /**
     * Get the W3C Traceparent representation for the span associated with this token, or null if the span has not started.
     * This needs to be unique for each instance.
     */
    fun asW3cTraceparent(): String?

    /**
     * Set the value of the attribute with the given key, overwriting the original value if it's already set
     */
    fun setSystemAttribute(key: String, value: String)

    /**
     * Set the value of the attribute with the given [EmbraceAttributeKey] to the result of [toString] on the value,
     * overwriting the original value if it's already set
     */
    fun setSystemAttribute(key: EmbraceAttributeKey, value: Any) = setSystemAttribute(key.name, value.toString())

    /**
     * Add an event to the span
     */
    fun addEvent(name: String, eventTimeMs: Long, attributes: Map<String, String>)
}
