package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.FlutterException.embFlutterExceptionContext
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.FlutterException.embFlutterExceptionLibrary
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Exception
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.FlutterException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.payload.EventType.ERROR_LOG
import io.embrace.android.embracesdk.internal.payload.EventType.INFO_LOG
import io.embrace.android.embracesdk.internal.payload.EventType.WARNING_LOG
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
public class EmbraceLogService(
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val backgroundWorker: BackgroundWorker,
    private val logger: EmbLogger,
    private val serializer: PlatformSerializer
) : LogService {

    private val logCounters = mapOf(
        Severity.INFO to LogCounter(
            Severity.INFO.name,
            configService.logMessageBehavior::getInfoLogLimit,
            logger
        ),
        Severity.WARNING to LogCounter(
            Severity.WARNING.name,
            configService.logMessageBehavior::getWarnLogLimit,
            logger
        ),
        Severity.ERROR to LogCounter(
            Severity.ERROR.name,
            configService.logMessageBehavior::getErrorLogLimit,
            logger
        )
    )

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
        // Currently, any call to this log method can only have an event type of INFO_LOG,
        // WARNING_LOG, or ERROR_LOG, since it is taken from the fromSeverity() method
        // in EventType.
        val severity = type.getSeverity()
        if (severity == null) {
            logger.logError("Invalid event type for log: $type")
            return
        }
        val redactedProperties = redactSensitiveProperties(properties)
        if (logExceptionType == LogExceptionType.NONE) {
            log(
                message,
                severity,
                redactedProperties
            )
        } else {
            val stacktrace = if (stackTraceElements != null) {
                serializer.truncatedStacktrace(stackTraceElements)
            } else {
                customStackTrace
            }
            if (configService.appFramework == AppFramework.FLUTTER) {
                logFlutterException(
                    message = message,
                    severity = severity,
                    logExceptionType = logExceptionType,
                    properties = redactedProperties,
                    stackTrace = stacktrace,
                    exceptionName = exceptionName,
                    exceptionMessage = exceptionMessage,
                    context = context,
                    library = library
                )
            } else {
                logException(
                    message = message,
                    severity = severity,
                    logExceptionType = logExceptionType,
                    properties = redactedProperties,
                    stackTrace = stacktrace,
                    exceptionName = exceptionName,
                    exceptionMessage = exceptionMessage
                )
            }
        }
    }

    private fun EventType.getSeverity(): Severity? {
        return when (this) {
            INFO_LOG -> Severity.INFO
            WARNING_LOG -> Severity.WARNING
            ERROR_LOG -> Severity.ERROR
            else -> null
        }
    }

    private fun log(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        backgroundWorker.submit {
            addLogEventData(
                message = message,
                severity = severity,
                attributes = createTelemetryAttributes(properties),
                schemaProvider = ::Log
            )
        }
    }

    private fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
    ) {
        backgroundWorker.submit {
            val attributes = createTelemetryAttributes(properties)
            populateLogExceptionAttributes(
                attributes = attributes,
                logExceptionType = logExceptionType,
                stackTrace = stackTrace,
                type = exceptionName,
                message = exceptionMessage,
            )

            addLogEventData(
                message = message,
                severity = severity,
                attributes = attributes,
                schemaProvider = ::Exception
            )
        }
    }

    private fun logFlutterException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
        context: String?,
        library: String?,
    ) {
        backgroundWorker.submit {
            val attributes = createTelemetryAttributes(properties)
            populateLogExceptionAttributes(
                attributes = attributes,
                logExceptionType = logExceptionType,
                stackTrace = stackTrace,
                type = exceptionName,
                message = exceptionMessage,
            )

            context?.let { attributes.setAttribute(embFlutterExceptionContext, it) }
            library?.let { attributes.setAttribute(embFlutterExceptionLibrary, it) }

            addLogEventData(
                message = message,
                severity = severity,
                attributes = attributes,
                schemaProvider = ::FlutterException
            )
        }
    }

    override fun getErrorLogsCount(): Int {
        return logCounters.getValue(Severity.ERROR).getCount()
    }

    override fun cleanCollections() {
        logCounters.forEach { it.value.clear() }
    }

    /**
     * Create [TelemetryAttributes] with the standard log properties
     */
    private fun createTelemetryAttributes(customProperties: Map<String, Any>?): TelemetryAttributes {
        val attributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = customProperties?.mapValues { it.value.toString() } ?: emptyMap()
        )

        attributes.setAttribute(LogIncubatingAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())

        return attributes
    }

    private fun populateLogExceptionAttributes(
        attributes: TelemetryAttributes,
        logExceptionType: LogExceptionType,
        stackTrace: String?,
        type: String?,
        message: String?,
    ) {
        attributes.setAttribute(embExceptionHandling, logExceptionType.value)
        type?.let { attributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, it) }
        message?.let { attributes.setAttribute(ExceptionAttributes.EXCEPTION_MESSAGE, it) }
        stackTrace?.let { attributes.setAttribute(ExceptionAttributes.EXCEPTION_STACKTRACE, it) }
    }

    private fun addLogEventData(
        message: String,
        severity: Severity,
        attributes: TelemetryAttributes,
        schemaProvider: (TelemetryAttributes) -> SchemaType
    ) {
        if (shouldLogBeGated(severity)) {
            return
        }

        val logId = attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID)
        if (logId == null || !logCounters.getValue(severity).addIfAllowed()) {
            return
        }

        logWriter.addLog(schemaProvider(attributes), severity.toOtelSeverity(), trimToMaxLength(message))
    }

    /**
     * Checks if the info or warning log event should be gated based on gating config. Error logs
     * should never be gated.
     *
     * @param severity of the log event
     * @return true if the log should be gated
     */
    private fun shouldLogBeGated(severity: Severity): Boolean {
        return when (severity) {
            Severity.INFO -> configService.sessionBehavior.shouldGateInfoLog()
            Severity.WARNING -> configService.sessionBehavior.shouldGateWarnLog()
            else -> false
        }
    }

    private fun trimToMaxLength(message: String): String {
        val maxLength = if (configService.appFramework == AppFramework.UNITY) {
            LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH
        } else {
            configService.logMessageBehavior.getLogMessageMaximumAllowedLength()
        }

        if (message.length > maxLength) {
            logger.logWarning("Truncating message of ${message.length} characters to $maxLength characters")
            val endChars = "..."

            if (maxLength <= endChars.length) {
                return message.take(maxLength)
            }

            return message.take(maxLength - endChars.length) + endChars
        } else {
            return message
        }
    }

    private fun redactSensitiveProperties(properties: Map<String, Any>?): Map<String, Any>? {
        return properties?.mapValues { (key, value) ->
            if (configService.sensitiveKeysBehavior.isSensitiveKey(key)) REDACTED_LABEL else value
        }
    }

    private companion object {

        /**
         * The default limit of Unity log messages that can be sent.
         */
        private const val LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH = 16384
    }
}
