package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal interface LogService {

    fun log(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?
    )

    fun logException(
        message: String,
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
