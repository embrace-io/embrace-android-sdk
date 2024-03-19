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
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logWarning
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal class EmbraceLogService(
    private val logWriter: LogWriter,
    private val clock: Clock,
    private val metadataService: MetadataService,
    private val configService: ConfigService,
    private val appFramework: AppFramework,
    private val sessionIdTracker: SessionIdTracker,
    private val backgroundWorker: BackgroundWorker
) : LogService {

    private val logCounters = mapOf(
        Severity.INFO to LogCounter(
            Severity.INFO.name,
            clock,
            configService.logMessageBehavior::getInfoLogLimit
        ),
        Severity.WARNING to LogCounter(
            Severity.WARNING.name,
            clock,
            configService.logMessageBehavior::getWarnLogLimit
        ),
        Severity.ERROR to LogCounter(
            Severity.ERROR.name,
            clock,
            configService.logMessageBehavior::getErrorLogLimit
        )
    )

    override fun log(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?
    ) {
        addLogEventData(message, severity, EmbraceLogAttributes(properties))
    }

    override fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        framework: AppFramework,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        val attributes = EmbraceLogAttributes(properties)
        attributes.setExceptionType(logExceptionType)
        attributes.setAppFramework(framework)
        // TBD: Add stacktrace elements
        exceptionName?.let { attributes.setExceptionName(it) }
        exceptionMessage?.let { attributes.setExceptionMessage(it) }
        context?.let { attributes.setExceptionContext(it) }
        library?.let { attributes.setExceptionLibrary(it) }
        addLogEventData(message, severity, attributes)
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

    override fun cleanCollections() {
        logCounters.forEach { it.value.clear() }
    }

    private fun addLogEventData(
        message: String,
        severity: Severity,
        attributes: EmbraceLogAttributes,
    ) {
        if (shouldLogBeGated(severity)) {
            return
        }

        backgroundWorker.submit {
            val messageId = Uuid.getEmbUuid()
            if (!logCounters.getValue(severity).addIfAllowed(messageId)) {
                return@submit
            }

            // Set these after the custom properties so they can't be overridden
            sessionIdTracker.getActiveSessionId()?.let { attributes.setSessionId(it) }
            metadataService.getAppState()?.let { attributes.setAppState(it) }
            attributes.setLogId(Uuid.getEmbUuid())

            val logEventData = LogEventData(
                schemaType = SchemaType.Log(attributes),
                message = trimToMaxLength(message),
                severity = severity,
            )

            logWriter.addLog(logEventData) { logEventData }
        }
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
            logWarning("Truncating message to ${message.length} characters")
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
    private val getConfigLogLimit: (() -> Int)
) {
    private val count = AtomicInteger(0)
    private val logIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val cache = CacheableValue<List<String>> { logIds.size }

    fun addIfAllowed(messageId: String): Boolean {
        val timestamp = clock.now()
        count.incrementAndGet()

        if (logIds.size < getConfigLogLimit.invoke()) {
            logIds[timestamp] = messageId
        } else {
            logWarning("$name log limit has been reached.")
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
