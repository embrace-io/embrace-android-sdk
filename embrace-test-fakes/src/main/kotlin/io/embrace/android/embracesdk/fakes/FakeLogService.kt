package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.payload.EventType

public class FakeLogService : LogService {
    public class LogData(
        public val message: String,
        public val type: EventType,
        public val logExceptionType: LogExceptionType,
        public val properties: Map<String, Any>?,
        public val stackTraceElements: Array<StackTraceElement>?,
        public val customStackTrace: String?,
        public val context: String?,
        public val library: String?,
        public val exceptionName: String?,
        public val exceptionMessage: String?
    )

    public val logs: MutableList<String> = mutableListOf()
    public val loggedMessages: MutableList<LogData> = mutableListOf()
    public var errorLogIds: List<String> = listOf()

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
