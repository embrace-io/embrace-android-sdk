package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryType

internal interface TelemetryData {
    val name: String
    val attributes: Map<String, String>
}

internal class TelemetryDataImpl(
    telemetryType: TelemetryType,
    schemaType: SchemaType
) : TelemetryData {
    override val name = schemaType.name
    override val attributes = schemaType.attrs.plus(telemetryType.toOTelKeyValuePair())
}
