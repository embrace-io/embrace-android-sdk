package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Declares functions for writing an [EmbraceSpanEvent] or attributes to the current session span.
 */
internal interface SessionSpanWriter {

    /**
     * Add an [EmbraceSpanEvent] with the given [name]. If [spanStartTimeMs] is null, the
     * current time will be used. Optionally, the specific
     * time of the event and a set of attributes can be passed in associated with the event.
     *
     * Callers should pass the object & a function reference that converts the object
     * to a [SpanEventData] object.
     *
     * Returns true if the event was added, otherwise false.
     */
    fun <T> addEvent(obj: T, mapper: T.() -> SpanEventData): Boolean

    /**
     * Add the given key-value pair as an Attribute to the Event.
     *
     * Returns true if the attribute was added, otherwise false.
     */
    fun addAttribute(attribute: SpanAttributeData): Boolean
}
