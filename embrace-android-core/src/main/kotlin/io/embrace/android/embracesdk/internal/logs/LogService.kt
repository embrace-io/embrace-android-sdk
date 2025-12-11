package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
interface LogService : SessionChangeListener {

    /**
     * Creates a remote log.
     */
    fun log(
        message: String,
        severity: LogSeverity,
        logExceptionType: LogExceptionType,
        attributes: Map<String, Any> = emptyMap(),
    )

    /**
     * Gets the number of error logs that have been recorded.
     *
     * @return the error logs count
     */
    fun getErrorLogsCount(): Int
}
