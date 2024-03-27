package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryType

/**
 * Represents a span event that can be added to the current session span.
 *
 * @param schemaType the type of the event. Used to differentiate data from different sources
 * by the backend.
 * @param spanStartTimeMs the start time of the span event in milliseconds.
 */
internal class SpanEventData(
    telemetryType: TelemetryType,
    schemaType: SchemaType,
    val spanStartTimeMs: Long
) : TelemetryData by TelemetryDataImpl(telemetryType, schemaType)
