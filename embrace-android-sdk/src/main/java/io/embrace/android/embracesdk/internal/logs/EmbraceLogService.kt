package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal class EmbraceLogService(
    private val logWriter: LogWriter,
    private val metadataService: MetadataService,
    private val configService: ConfigService,
    private val appFramework: AppFramework,
    private val sessionIdTracker: SessionIdTracker,
    private val sessionProperties: EmbraceSessionProperties,
    private val backgroundWorker: BackgroundWorker,
    private val logger: InternalEmbraceLogger,
    clock: Clock,
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
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        backgroundWorker.submit {
            val attributes = LogAttributes(properties)
            populateLogAttributes(attributes)
            addLogEventData(
                message,
                severity,
                attributes,
                SchemaType.Log(attributes),
            )
        }
    }

    override fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        framework: AppFramework,
        exceptionName: String?,
        exceptionMessage: String?,
    ) {
        backgroundWorker.submit {
            if (logExceptionType == LogExceptionType.UNHANDLED) {
                unhandledExceptionsCount++
            }
            val attributes = ExceptionAttributes(properties)
            populateLogExceptionAttributes(
                attributes,
                logExceptionType,
                stackTrace,
                exceptionName,
                exceptionMessage,
            )
            addLogEventData(
                message,
                severity,
                attributes,
                SchemaType.Exception(attributes),
            )
        }
    }

    override fun logFlutterException(
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
            val attributes = FlutterExceptionAttributes(properties)
            populateLogFlutterExceptionAttributes(
                attributes,
                logExceptionType,
                stackTrace,
                exceptionName,
                exceptionMessage,
                context,
                library,
            )
            addLogEventData(
                message,
                severity,
                attributes,
                SchemaType.FlutterException(attributes),
            )
        }
    }

    override fun getInfoLogsAttemptedToSend(): Int {
        return logCounters.getValue(Severity.INFO).getCount()
    }

    override fun getWarnLogsAttemptedToSend(): Int {
        return logCounters.getValue(Severity.WARNING).getCount()
    }

    override fun getErrorLogsAttemptedToSend(): Int {
        return logCounters.getValue(Severity.ERROR).getCount()
    }

    override fun findInfoLogIds(startTime: Long, endTime: Long): List<String> {
        return logCounters.getValue(Severity.INFO).findLogIds(startTime, endTime)
    }

    override fun findWarningLogIds(startTime: Long, endTime: Long): List<String> {
        return logCounters.getValue(Severity.WARNING).findLogIds(startTime, endTime)
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return logCounters.getValue(Severity.ERROR).findLogIds(startTime, endTime)
    }

    override fun getUnhandledExceptionsSent(): Int {
        return unhandledExceptionsCount
    }

    override fun cleanCollections() {
        logCounters.forEach { it.value.clear() }
    }

    /**
     * Populates Log common attributes.
     * Set these after the adding custom properties to attributes,
     * so they can't be overridden.
     */
    private fun populateLogAttributes(attributes: LogAttributes) {
        attributes.setLogId(Uuid.getEmbUuid())
        sessionIdTracker.getActiveSessionId()?.let { attributes.setSessionId(it) }
        metadataService.getAppState()?.let { attributes.setAppState(it) }
        attributes.setSessionProperties(sessionProperties.get())
    }

    private fun populateLogExceptionAttributes(
        attributes: ExceptionAttributes,
        logExceptionType: LogExceptionType,
        stackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
    ) {
        populateLogAttributes(attributes)
        attributes.setExceptionHandling(logExceptionType)
        stackTrace?.let { attributes.setExceptionStacktrace(it) }
        exceptionName?.let { attributes.setExceptionName(it) }
        exceptionMessage?.let { attributes.setExceptionMessage(it) }
    }

    private fun populateLogFlutterExceptionAttributes(
        attributes: FlutterExceptionAttributes,
        logExceptionType: LogExceptionType,
        stackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
        context: String?,
        library: String?,
    ) {
        populateLogExceptionAttributes(
            attributes,
            logExceptionType,
            stackTrace,
            exceptionName,
            exceptionMessage,
        )
        context?.let { attributes.setContext(it) }
        library?.let { attributes.setLibrary(it) }
    }

    private fun addLogEventData(
        message: String,
        severity: Severity,
        attributes: LogAttributes,
        schemaType: SchemaType,
    ) {
        if (shouldLogBeGated(severity)) {
            return
        }

        val logId = attributes.getLogId() ?: return
        if (!logCounters.getValue(severity).addIfAllowed(logId)) {
            return
        }

        val logEventData = LogEventData(
            schemaType = schemaType,
            message = trimToMaxLength(message),
            severity = severity,
        )

        logWriter.addLog(logEventData) { logEventData }
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
    private val logger: InternalEmbraceLogger
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
            logger.logWarning("$name log limit has been reached.")
            return false
        }
        return true
    }

    fun findLogIds(
        startTime: Long,
        endTime: Long,
    ): List<String> {
        return cache.value { ArrayList(logIds.subMap(startTime, endTime).values) }
    }

    fun getCount(): Int = count.get()

    fun clear() {
        count.set(0)
        logIds.clear()
    }
}
