package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Allows to switch between the current service that sends Embrace logs as Events and the new one
 * that sends them as OTel logs, based on a remote configuration. Once the SDK is expected to send
 * only OTel logs, this class can be removed.
 */
internal class CompositeLogService(
    private val logService: Provider<LogService>,
) : LogMessageService {

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
        logService().log(
            message,
            type,
            logExceptionType,
            properties,
            stackTraceElements,
            customStackTrace,
            framework,
            context,
            library,
            exceptionName,
            exceptionMessage
        )
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return logService().findErrorLogIds(startTime, endTime)
    }

    override fun cleanCollections() {
        return logService().cleanCollections()
    }
}
