package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.arch.schema.SchemaType

internal class FakeLogWriter : LogWriter {

    val logEvents = mutableListOf<LogEventData>()

    override fun addLog(schemaType: SchemaType, severity: Severity, message: String, isPrivate: Boolean) {
        logEvents.add(LogEventData(schemaType, severity, message))
    }
}
