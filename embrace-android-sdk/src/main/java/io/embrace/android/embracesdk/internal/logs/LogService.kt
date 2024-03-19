package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.session.MemoryCleanerListener

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal interface LogService : MemoryCleanerListener {

    /**
     * Creates a log record.
     *
     * @param message        the message to log
     * @param severity       severity associated to the log, one INFO, WARNING, or ERROR
     * @param properties     custom properties to send as part of the event
     */
    fun log(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?
    )

    /**
     * Creates a remote log.
     *
     * @param message            the message to log
     * @param logExceptionType   whether the log is a handled exception, unhandled, or non an exception
     * @param properties         custom properties to send as part of the event
     * @param stackTraceElements the stacktrace elements of a throwable
     * @param customStackTrace   stacktrace string for non-JVM exceptions
     * @param framework          the app framework (Native, Unity, etc) for the exception
     * @param context            context for a Dart exception from the Flutter SDK
     * @param library            library from a Dart exception from the Flutter SDK
     * @param exceptionName      the exception name of a Throwable is it is present
     * @param exceptionMessage   the exception message of a Throwable is it is present
     */
    @Suppress("LongParameterList")
    fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        framework: AppFramework,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    )

    /**
     * Finds all IDs of log events at info level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findInfoLogIds(startTime: Long, endTime: Long): List<String>

    /**
     * Finds all IDs of log events at warning level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findWarningLogIds(startTime: Long, endTime: Long): List<String>

    /**
     * Finds all IDs of log events at error level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findErrorLogIds(startTime: Long, endTime: Long): List<String>

    /**
     * The total number of info logs that the app attempted to send.
     */
    fun getInfoLogsAttemptedToSend(): Int

    /**
     * The total number of warning logs that the app attempted to send.
     */
    fun getWarnLogsAttemptedToSend(): Int

    /**
     * The total number of error logs that the app attempted to send.
     */
    fun getErrorLogsAttemptedToSend(): Int
}
