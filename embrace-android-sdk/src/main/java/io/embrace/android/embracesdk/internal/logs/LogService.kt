package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal interface LogService : BaseLogService {

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
     * @param stackTrace         stacktrace string for non-JVM exceptions
     * @param framework          the app framework (Native, Unity, etc) for the exception
     * @param exceptionName      the exception name of a Throwable is it is present
     * @param exceptionMessage   the exception message of a Throwable is it is present
     */
    @Suppress("LongParameterList")
    fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        framework: AppFramework,
        exceptionName: String?,
        exceptionMessage: String?
    )

    /**
     * Creates a log for a Flutter exception.
     *
     * @param message           the message to log
     * @param severity          severity associated to the log, one INFO, WARNING, or ERROR
     * @param logExceptionType  whether the log is a handled exception, unhandled, or non an exception
     * @param properties        custom properties to send as part of the event
     * @param stackTrace        stacktrace string for non-JVM exceptions
     * @param context           the context of the exception
     * @param library           the library of the exception
     * @param exceptionName     the exception name of a Throwable is it is present
     * @param exceptionMessage  the exception message of a Throwable is it is present
     * @param context           the context of the exception
     * @param library           the library of the exception
     *
     */
    fun logFlutterException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
        context: String?,
        library: String?,
    )
}
