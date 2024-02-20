package io.embrace.android.embracesdk.arch.destination

/**
 * Declares functions for writing a [LogEventData] to the current session span.
 */
internal interface LogWriter {
    fun addLog(log: LogEventData)
}
