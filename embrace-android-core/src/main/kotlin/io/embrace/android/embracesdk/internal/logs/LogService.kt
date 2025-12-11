package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
interface LogService : SessionChangeListener {

    /**
     * Creates a remote log.
     */
    fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        attributes: Map<String, Any> = emptyMap(),
        embraceAttributes: Map<String, String> = emptyMap(),
        attachment: Attachment? = null,
        stackTraceElements: Array<StackTraceElement>? = null,
        customStackTrace: String? = null,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
    )

    /**
     * Gets the number of error logs that have been recorded.
     *
     * @return the error logs count
     */
    fun getErrorLogsCount(): Int
}
