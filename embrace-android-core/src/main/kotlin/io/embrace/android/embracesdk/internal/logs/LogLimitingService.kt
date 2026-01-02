package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity

/**
 * Restricts the number of logs per session
 */
interface LogLimitingService : SessionChangeListener {
    /**
     * Get the current count of logs since the service was last reset
     */
    fun getCount(logSeverity: LogSeverity): Int

    /**
     * Return true if a log of the given severity can be recorded currently
     */
    fun addIfAllowed(logSeverity: LogSeverity): Boolean
}
