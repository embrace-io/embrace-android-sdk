package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.SchemaType

/**
 * Represents a span event that can be added to the current session span.
 *
 * @param schemaType the type of the event. Used to differentiate data from different sources
 * by the backend.
 * @param spanStartTimeMs the start time of the span event in milliseconds.
 */
internal class SpanEventData(
    val schemaType: SchemaType,
    val spanStartTimeMs: Long
) {
    val attributes = schemaType.attrs.plus(schemaType.telemetryType.toOTelKeyValuePair())
}
