package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.otel.schema.EmbType

/**
 * Declares functions for writing an [EmbraceSpanEvent] or attributes to the current session span.
 */
interface SessionSpanWriter {

    /**
     * Add a span event for the given [schemaType] to the session span. If [spanStartTimeMs] is null, the
     * current time will be used. Returns true if the event was added, otherwise false.
     */
    fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean

    /**
     * Remove all span events with the given [EmbType].
     */
    fun removeSessionEvents(type: EmbType)

    /**
     * Add the given key-value pair as an Attribute to the session span
     */
    fun addSessionAttribute(attribute: SpanAttributeData)

    /**
     * Remove the attribute with the given key
     */
    fun removeSessionAttribute(key: String)
}
