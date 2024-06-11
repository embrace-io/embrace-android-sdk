package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger

/**
 * Allows to switch between the current service that sends Embrace logs as Events and the new one
 * that sends them as OTel logs, based on a remote configuration. Once the SDK is expected to send
 * only OTel logs, this class can be removed.
 */
internal class CompositeLogService(
    private val v2LogService: Provider<LogService>,
    private val logger: EmbLogger,
    private val serializer: EmbraceSerializer
) : LogMessageService {

    private val baseLogService: BaseLogService
        get() = v2LogService()

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
        // When LogMessageService is removed, the cascade calling of event-based logMessage()
        // in EmbraceImpl can be replaced with different calls to LogService

        // Currently, any call to this log method can only have an event type of INFO_LOG,
        // WARNING_LOG, or ERROR_LOG, since it is taken from the fromSeverity() method
        // in EventType.
        if (type.getSeverity() == null) {
            logger.logError("Invalid event type for log: $type")
            return
        }
        val severity = type.getSeverity() ?: Severity.INFO
        if (logExceptionType == LogExceptionType.NONE) {
            v2LogService().log(
                message,
                severity,
                properties
            )
        } else {
            val stacktrace = if (stackTraceElements != null) {
                serializer.truncatedStacktrace(stackTraceElements)
            } else {
                customStackTrace
            }
            if (framework == Embrace.AppFramework.FLUTTER) {
                v2LogService().logFlutterException(
                    message = message,
                    severity = severity,
                    logExceptionType = logExceptionType,
                    properties = properties,
                    stackTrace = stacktrace,
                    exceptionName = exceptionName,
                    exceptionMessage = exceptionMessage,
                    context = context,
                    library = library
                )
            } else {
                v2LogService().logException(
                    message = message,
                    severity = severity,
                    logExceptionType = logExceptionType,
                    properties = properties,
                    stackTrace = stacktrace,
                    framework = framework,
                    exceptionName = exceptionName,
                    exceptionMessage = exceptionMessage
                )
            }
        }
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return baseLogService.findErrorLogIds(startTime, endTime)
    }

    override fun cleanCollections() {
        return baseLogService.cleanCollections()
    }
}
