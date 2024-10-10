package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.opentelemetry.api.logs.Severity

class FakeLogWriter : LogWriter {

    val logEvents: MutableList<LogEventData> = mutableListOf()

    override fun addLog(
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        addCurrentSessionId: Boolean,
    ) {
        logEvents.add(LogEventData(schemaType, severity, message))
    }
}
