package io.embrace.android.embracesdk.arch.destination

internal class LogWriterImpl : LogWriter {

    override fun <T> addLog(log: T, mapper: T.() -> LogEventData) {
        // no-op
    }
}
