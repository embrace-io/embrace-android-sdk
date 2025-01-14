package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.opentelemetry.api.common.AttributeKey

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
interface LogService : MemoryCleanerListener {

    /**
     * Creates a remote log.
     *
     * @param message            the message to log
     * @param severity           the log severity
     * @param logExceptionType   whether the log is a handled exception, unhandled, or non an exception
     * @param properties         custom properties to send as part of the event
     */
    fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>? = null,
        customLogAttrs: Map<AttributeKey<String>, String> = emptyMap(),
    )

    /**
     * Gets the number of error logs that have been recorded.
     *
     * @return the error logs count
     */
    fun getErrorLogsCount(): Int
}
