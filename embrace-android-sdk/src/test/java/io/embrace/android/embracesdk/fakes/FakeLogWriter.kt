package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter

internal class FakeLogWriter : LogWriter {

    val logEvents = mutableListOf<LogEventData>()

    override fun <T> addLog(log: T, mapper: (T.() -> LogEventData)?) {
        val logEvent = if (log is LogEventData) {
            log
        } else if (mapper != null) {
            log.mapper()
        } else {
            return
        }

        logEvents.add(logEvent)
    }
}
