package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter

internal class FakeOpenTelemetryLogWriter : LogWriter {

    val logEvents = mutableListOf<LogEventData>()

    override fun addLog(log: LogEventData) {
        logEvents.add(log)
    }
}