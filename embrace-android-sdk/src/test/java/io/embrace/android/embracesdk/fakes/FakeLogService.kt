package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.logs.LogService

internal class FakeLogService : LogService {
    internal class LogData(
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

    val logs = mutableListOf<String>()
    val loggedMessages = mutableListOf<LogData>()
    var errorLogIds = listOf<String>()

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

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return errorLogIds
    }

    override fun cleanCollections() {
        TODO("Not yet implemented")
    }
}
