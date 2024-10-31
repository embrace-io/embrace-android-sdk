package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.logs.LogService

class FakeLogService : LogService {
    class LogData(
        val message: String,
        val severity: Severity,
        val logExceptionType: LogExceptionType,
        val properties: Map<String, Any>?,
        val stackTraceElements: Array<StackTraceElement>?,
        val customStackTrace: String?,
        val context: String?,
        val library: String?,
        val exceptionName: String?,
        val exceptionMessage: String?,
    )

    val logs: MutableList<String> = mutableListOf()
    val loggedMessages: MutableList<LogData> = mutableListOf()
    var errorLogIds: List<String> = listOf()

    override fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?,
    ) {
        loggedMessages.add(
            LogData(
                message = message,
                severity = severity,
                logExceptionType = logExceptionType,
                properties = properties,
                stackTraceElements = stackTraceElements,
                customStackTrace = customStackTrace,
                context = context,
                library = library,
                exceptionName = exceptionName,
                exceptionMessage = exceptionMessage
            )
        )
    }

    override fun getErrorLogsCount(): Int {
        return errorLogIds.count()
    }

    override fun cleanCollections() {
        TODO("Not yet implemented")
    }
}
