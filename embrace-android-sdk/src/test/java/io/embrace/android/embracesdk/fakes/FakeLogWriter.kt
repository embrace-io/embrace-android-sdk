package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter

internal class FakeLogWriter : LogWriter {

    val logs: MutableList<LogEventData> = mutableListOf()

    override fun <T> addLog(log: T, mapper: T.() -> LogEventData) {
        val obj = log.mapper()
        logs.add(obj)
    }
}
