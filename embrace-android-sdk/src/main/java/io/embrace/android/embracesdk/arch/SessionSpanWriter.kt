package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Declares functions for writing an [EmbraceSpanEvent] or attributes to the current session span.
 */
internal interface SessionSpanWriter {

    /**
     * Add an [EmbraceSpanEvent] with the given [name]. If [timeNanos] is null, the
     * current time will be used. Optionally, the specific
     * time of the event and a set of attributes can be passed in associated with the event.
     *
     * Returns true if the event was added, otherwise false.
     */
    fun addEvent(event: SpanEventData): Boolean

    /**
     * Add the given key-value pair as an Attribute to the Event.
     *
     * Returns true if the attribute was added, otherwise false.
     */
    fun addAttribute(attribute: SpanAttributeData): Boolean
}

/**
 * Represents a span event that can be added to the current session span.
 */
internal class SpanEventData(
    val spanName: String,
    val spanStartTimeNanos: Long,
    val attributes: Map<String, String>?
)

/**
 * Represents a span attribute that can be added to the current session span.
 */
internal class SpanAttributeData(
    val key: String,
    val value: String
)
