package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.payload.EventType

class FakeLogService : LogService {
    class LogData(
        val message: String,
        val type: EventType,
        val logExceptionType: LogExceptionType,
        val properties: Map<String, Any>?,
        val stackTraceElements: Array<StackTraceElement>?,
        val customStackTrace: String?,
        val context: String?,
        val library: String?,
        val exceptionName: String?,
        val exceptionMessage: String?
    )

    val logs: MutableList<String> = mutableListOf()
    val loggedMessages: MutableList<LogData> = mutableListOf()
    var errorLogIds: List<String> = listOf()

    override fun log(
        message: String,
        type: EventType,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        loggedMessages.add(
            LogData(
                message = message,
                type = type,
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
