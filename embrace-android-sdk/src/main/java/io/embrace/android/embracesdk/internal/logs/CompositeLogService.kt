package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkCapturedCall

/**
 * Allows to switch between the current service that sends Embrace logs as Events and the new one
 * that sends them as OTel logs, based on a remote configuration. Once the SDK is expected to send
 * only OTel logs, this class can be removed.
 */
internal class CompositeLogService(
    private val v1LogService: LogMessageService,
    private val v2LogService: LogService,
    private val configService: ConfigService
) : LogMessageService {

    private val useV2LogService: Boolean
        get() = configService.sessionBehavior.useV2Payload()

    private val baseLogService: BaseLogService
        get() = if (useV2LogService) v2LogService else v1LogService

    override fun logNetwork(networkCaptureCall: NetworkCapturedCall?) {
        // Network logs are still always handled by the v1 LogMessageService
        v1LogService.logNetwork(networkCaptureCall)
    }

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
        if (useV2LogService) {
            // Currently, any call to this log method can only have an event type of INFO_LOG,
            // WARNING_LOG, or ERROR_LOG, since it is taken from the fromSeverity() method
            // in EventType.
            if (type.getSeverity() == null) {
                InternalStaticEmbraceLogger.logError("Invalid event type for log: $type")
                return
            }
            val severity = type.getSeverity() ?: Severity.INFO
            if (logExceptionType == LogExceptionType.NONE) {
                v2LogService.log(
                    message,
                    severity,
                    properties
                )
            } else {
                // Currently, the backend is not processing exceptions as OTel logs, so we must
                // use v1. When the backend is ready, this must be replaced with a call
                // to v2LogService.logException().
                v1LogService.log(
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
            }
        } else {
            v1LogService.log(
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
        }
    }

    override fun findInfoLogIds(startTime: Long, endTime: Long): List<String> {
        return baseLogService.findInfoLogIds(startTime, endTime)
    }

    override fun findWarningLogIds(startTime: Long, endTime: Long): List<String> {
        return baseLogService.findWarningLogIds(startTime, endTime)
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return baseLogService.findErrorLogIds(startTime, endTime)
    }

    override fun findNetworkLogIds(startTime: Long, endTime: Long): List<String> {
        // Network logs are still always handled by the v1 LogMessageService
        return v1LogService.findNetworkLogIds(startTime, endTime)
    }

    override fun getInfoLogsAttemptedToSend(): Int {
        return baseLogService.getInfoLogsAttemptedToSend()
    }

    override fun getWarnLogsAttemptedToSend(): Int {
        return baseLogService.getWarnLogsAttemptedToSend()
    }

    override fun getErrorLogsAttemptedToSend(): Int {
        return baseLogService.getErrorLogsAttemptedToSend()
    }

    override fun getUnhandledExceptionsSent(): Int {
        return baseLogService.getUnhandledExceptionsSent()
    }

    override fun cleanCollections() {
        return baseLogService.cleanCollections()
    }
}
