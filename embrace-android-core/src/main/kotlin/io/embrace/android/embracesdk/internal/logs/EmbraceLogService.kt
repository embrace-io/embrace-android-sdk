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
import io.embrace.android.embracesdk.internal.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
class EmbraceLogService(
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val serializer: PlatformSerializer,
) : LogService {

    private val logCounters = mapOf(
        Severity.INFO to LogCounter(
            configService.logMessageBehavior::getInfoLogLimit
        ),
        Severity.WARNING to LogCounter(
            configService.logMessageBehavior::getWarnLogLimit
        ),
        Severity.ERROR to LogCounter(
            configService.logMessageBehavior::getErrorLogLimit
        )
    )

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

    private fun log(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        addLogEventData(
            message = message,
            severity = severity,
            attributes = createTelemetryAttributes(properties),
            schemaProvider = ::Log
        )
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
        schemaProvider: (TelemetryAttributes) -> SchemaType,
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
