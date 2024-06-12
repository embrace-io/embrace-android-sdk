package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
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
     * Returns true if the event was added, otherwise false.
     */
    fun addEvent(schemaType: SchemaType, startTimeMs: Long): Boolean

    /**
     * Remove all events with the given [EmbType].
     */
    fun removeEvents(type: EmbType)

    /**
     * Add the given key-value pair as an Attribute to the Event.
     *
     * Returns true if the attribute was added, otherwise false.
     */
    fun addCustomAttribute(attribute: SpanAttributeData): Boolean

    /**
     * Remove the attribute with the given key
     *
     * Returns true if attribute was removed, otherwise false.
     */
    fun removeCustomAttribute(key: String): Boolean
}
