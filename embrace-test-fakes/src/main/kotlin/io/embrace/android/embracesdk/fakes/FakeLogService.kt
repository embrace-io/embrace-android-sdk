package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.logs.LogService

class FakeLogService : LogService {
    class LogData(
        val message: String,
        val severity: LogSeverity,
        val schemaType: SchemaType,
        val attributes: Map<String, Any>,
    )

    val logs: MutableList<String> = mutableListOf()
    val loggedMessages: MutableList<LogData> = mutableListOf()

    override fun log(
        message: String,
        severity: LogSeverity,
        attributes: Map<String, Any>,
        schemaProvider: (TelemetryAttributes) -> SchemaType,
    ) {
        loggedMessages.add(
            LogData(
                message = message,
                severity = severity,
                attributes = attributes,
                schemaType = schemaProvider(TelemetryAttributes())
            )
        )
    }
}
