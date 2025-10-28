package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Represents a span event that can be added to the current session span.
 *
 * @param schemaType the type of the event. Used to differentiate data from different sources
 * by the backend.
 * @param startTimeMs the start time of the span event in milliseconds.
 */
class SpanEventData(
    val schemaType: SchemaType,
    val startTimeMs: Long,
)
