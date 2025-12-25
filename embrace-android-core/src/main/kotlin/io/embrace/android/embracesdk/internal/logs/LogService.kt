package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
interface LogService {

    /**
     * Creates a remote log.
     */
    fun log(
        message: String,
        severity: LogSeverity,
        attributes: Map<String, Any>,
        schemaProvider: (TelemetryAttributes) -> SchemaType,
    )
}
