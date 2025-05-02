package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber

class FakeLogWriter : LogWriter {

    val logEvents: MutableList<LogEventData> = mutableListOf()

    override fun addLog(
        schemaType: SchemaType,
        severity: SeverityNumber,
        message: String,
        isPrivate: Boolean,
        addCurrentSessionInfo: Boolean,
        timestampMs: Long?,
    ) {
        logEvents.add(LogEventData(schemaType, severity, message))
    }
}
