package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal interface LogService {

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
     * @param context            context for a Dart exception from the Flutter SDK
     * @param library            library from a Dart exception from the Flutter SDK
     * @param exceptionName      the exception name of a Throwable is it is present
     * @param exceptionMessage   the exception message of a Throwable is it is present
     */
    fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    )
}
