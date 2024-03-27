package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryType

/**
 * Holds the information required to start a span.
 *
 * @param schemaType the type of the span. Used to differentiate data from different sources
 * by the backend.
 * @param spanStartTimeMs the start time of the span event in milliseconds.
 */
internal class StartSpanData(
    telemetryType: TelemetryType,
    schemaType: SchemaType,
    val spanStartTimeMs: Long,
) : TelemetryData by TelemetryDataImpl(telemetryType, schemaType)
