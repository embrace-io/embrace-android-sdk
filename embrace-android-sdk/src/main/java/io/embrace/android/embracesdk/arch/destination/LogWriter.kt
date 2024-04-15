package io.embrace.android.embracesdk.arch.destination

/**
 * Declares functions for writing a [LogEventData] to the current session span.
 */
internal interface LogWriter {
    fun <T> addLog(log: T, mapper: (T.() -> LogEventData)? = null)
}
