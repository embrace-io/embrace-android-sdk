package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.logs.LogService

class FakeLogService : LogService {
    class LogData(
        val message: String,
        val severity: LogSeverity,
        val logExceptionType: LogExceptionType,
        val embraceAttributes: Map<String, Any>,
    )

    val logs: MutableList<String> = mutableListOf()
    val loggedMessages: MutableList<LogData> = mutableListOf()
    var errorLogIds: List<String> = listOf()

    override fun log(
        message: String,
        severity: LogSeverity,
        logExceptionType: LogExceptionType,
        attributes: Map<String, Any>,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
    ) {
        loggedMessages.add(
            LogData(
                message = message,
                severity = severity,
                logExceptionType = logExceptionType,
                embraceAttributes = attributes,
            )
        )
    }

    override fun getErrorLogsCount(): Int {
        return errorLogIds.count()
    }

    override fun onPostSessionChange() {}
}
