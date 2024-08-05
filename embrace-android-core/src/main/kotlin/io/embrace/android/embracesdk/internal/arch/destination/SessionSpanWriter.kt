package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Declares functions for writing an [EmbraceSpanEvent] or attributes to the current session span.
 */
public interface SessionSpanWriter {

    /**
     * Add a span event for the given [schemaType] to the session span. If [spanStartTimeMs] is null, the
     * current time will be used. Returns true if the event was added, otherwise false.
     */
    public fun addEvent(schemaType: SchemaType, startTimeMs: Long): Boolean

    /**
     * Remove all span events with the given [EmbType].
     */
    public fun removeEvents(type: EmbType)

    /**
     * Add the given key-value pair as an Attribute to the session span
     *
     * Returns true if the attribute was added, otherwise false.
     */
    public fun addCustomAttribute(attribute: SpanAttributeData): Boolean

    /**
     * Remove the attribute with the given key
     *
     * Returns true if attribute was removed, otherwise false.
     */
    public fun removeCustomAttribute(key: String): Boolean
}
