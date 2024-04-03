package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.SchemaType

/**
 * Holds the information required to start a span.
 *
 * @param schemaType the type of the span. Used to differentiate data from different sources
 * by the backend.
 * @param spanStartTimeMs the start time of the span event in milliseconds.
 */
internal class StartSpanData(
    val schemaType: SchemaType,
    val spanStartTimeMs: Long,
)
