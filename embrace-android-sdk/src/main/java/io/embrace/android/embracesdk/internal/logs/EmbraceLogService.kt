package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.arch.schema.EmbType.System.FlutterException.embFlutterExceptionContext
import io.embrace.android.embracesdk.arch.schema.EmbType.System.FlutterException.embFlutterExceptionLibrary
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.SchemaType.Exception
import io.embrace.android.embracesdk.arch.schema.SchemaType.FlutterException
import io.embrace.android.embracesdk.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal class EmbraceLogService(
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val appFramework: AppFramework,
    private val sessionProperties: EmbraceSessionProperties,
    private val backgroundWorker: BackgroundWorker,
    private val logger: EmbLogger,
    clock: Clock,
    private val serializer: EmbraceSerializer
) : LogService {

    private val logCounters = mapOf(
        Severity.INFO to LogCounter(
            Severity.INFO.name,
            clock,
            configService.logMessageBehavior::getInfoLogLimit,
            logger
        ),
        Severity.WARNING to LogCounter(
            Severity.WARNING.name,
            clock,
            configService.logMessageBehavior::getWarnLogLimit,
            logger
        ),
        Severity.ERROR to LogCounter(
            Severity.ERROR.name,
            clock,
            configService.logMessageBehavior::getErrorLogLimit,
            logger
        )
    )

    private var unhandledExceptionsCount = 0

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
        if (type.getSeverity() == null) {
            logger.logError("Invalid event type for log: $type")
            return
        }
        val severity = type.getSeverity() ?: Severity.INFO
        if (logExceptionType == LogExceptionType.NONE) {
            log(
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
            if (appFramework == AppFramework.FLUTTER) {
                logFlutterException(
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
                logException(
                    message = message,
                    severity = severity,
                    logExceptionType = logExceptionType,
                    properties = properties,
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
            if (logExceptionType == LogExceptionType.UNHANDLED) {
                unhandledExceptionsCount++
            }

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
            if (logExceptionType == LogExceptionType.UNHANDLED) {
                unhandledExceptionsCount++
            }

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

    override fun findErrorLogIds(): List<String> {
        return logCounters.getValue(Severity.ERROR).findLogIds()
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
            sessionProperties = sessionProperties,
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
        type?.let { attributes.setAttribute(ExceptionIncubatingAttributes.EXCEPTION_TYPE, it) }
        message?.let { attributes.setAttribute(ExceptionIncubatingAttributes.EXCEPTION_MESSAGE, it) }
        stackTrace?.let { attributes.setAttribute(ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE, it) }
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
        if (logId == null || !logCounters.getValue(severity).addIfAllowed(logId)) {
            return
        }

        logWriter.addLog(schemaProvider(attributes), severity, trimToMaxLength(message))
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
        val maxLength =
            if (appFramework == AppFramework.UNITY) {
                LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH
            } else {
                configService.logMessageBehavior.getLogMessageMaximumAllowedLength()
            }

        return if (message.length > maxLength) {
            val endChars = "..."

            // ensure that we never end up with a negative offset when extracting substring, regardless of the config value set
            val allowedLength = when {
                maxLength >= endChars.length -> maxLength - endChars.length
                else -> LogMessageBehavior.LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH - endChars.length
            }
            logger.logWarning("Truncating message to ${message.length} characters")
            message.substring(0, allowedLength) + endChars
        } else {
            message
        }
    }

    companion object {

        /**
         * The default limit of Unity log messages that can be sent.
         */
        private const val LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH = 16384
    }
}

internal class LogCounter(
    private val name: String,
    private val clock: Clock,
    private val getConfigLogLimit: (() -> Int),
    private val logger: EmbLogger
) {
    private val count = AtomicInteger(0)
    private val logIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val cache = CacheableValue<List<String>> { logIds.size }

    fun addIfAllowed(logId: String): Boolean {
        val timestamp = clock.now()
        count.incrementAndGet()

        if (logIds.size < getConfigLogLimit.invoke()) {
            logIds[timestamp] = logId
        } else {
            logger.logInfo("$name log limit has been reached.")
            return false
        }
        return true
    }

    fun findLogIds(): List<String> {
        return cache.value { ArrayList(logIds.values) }
    }

    fun getCount(): Int = count.get()

    fun clear() {
        count.set(0)
        logIds.clear()
    }
}
