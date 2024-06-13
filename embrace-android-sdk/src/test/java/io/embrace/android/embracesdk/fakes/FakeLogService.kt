package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.logs.LogService

internal class FakeLogService : LogService {

    val logs = mutableListOf<String>()
    val loggedMessages = mutableListOf<FakeLogMessageService.LogData>()
    var errorLogIds = listOf<String>()

    override fun log(
        message: String,
        type: EventType,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        framework: Embrace.AppFramework,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        loggedMessages.add(
            FakeLogMessageService.LogData(
                message = message,
                type = type,
                logExceptionType = logExceptionType,
                properties = properties,
                stackTraceElements = stackTraceElements,
                customStackTrace = customStackTrace,
                framework = framework,
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
