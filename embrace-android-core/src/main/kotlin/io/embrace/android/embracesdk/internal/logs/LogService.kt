package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
interface LogService : MemoryCleanerListener {

    /**
     * Creates a remote log.
     *
     * @param message            the message to log
     * @param type               the type of message to log, which must be INFO_LOG, WARNING_LOG, or ERROR_LOG
     * @param logExceptionType   whether the log is a handled exception, unhandled, or non an exception
     * @param properties         custom properties to send as part of the event
     * @param stackTraceElements the stacktrace elements of a throwable
     * @param customStackTrace   stacktrace string for non-JVM exceptions
     * @param context            the context of the exception
     * @param library            the library of the exception
     * @param exceptionName      the exception name of a Throwable is it is present
     * @param exceptionMessage   the exception message of a Throwable is it is present
     */
    @Suppress("LongParameterList")
    fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>? = null,
        stackTraceElements: Array<StackTraceElement>? = null,
        customStackTrace: String? = null,
        context: String? = null,
        library: String? = null,
        exceptionName: String? = null,
        exceptionMessage: String? = null
    )

    /**
     * Gets the number of error logs that have been recorded.
     *
     * @return the error logs count
     */
    fun getErrorLogsCount(): Int
}
