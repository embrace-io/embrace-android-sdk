package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter

internal class FakeOpenTelemetryLogWriter : LogWriter {

    val logEvents = mutableListOf<LogEventData>()

    override fun <T> addLog(log: T, mapper: T.() -> LogEventData) {
        val logEvent = log.mapper()
        logEvents.add(logEvent)
    }
}
