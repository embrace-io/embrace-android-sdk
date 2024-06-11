package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.Stacktraces
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Logs messages remotely, so that they can be viewed as events during a user's session.
 */
internal class EmbraceLogMessageService(
    private val metadataService: MetadataService,
    private val sessionIdTracker: SessionIdTracker,
    private val deliveryService: DeliveryService,
    private val userService: UserService,
    private val configService: ConfigService,
    private val sessionProperties: EmbraceSessionProperties,
    private val logger: EmbLogger,
    private val clock: Clock,
    private val backgroundWorker: BackgroundWorker,
    private val gatingService: GatingService,
) : LogMessageService {

    private val lock = Any()
    private val infoLogIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val warningLogIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val errorLogIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val networkLogIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val logsInfoCount = AtomicInteger(0)
    private val logsErrorCount = AtomicInteger(0)
    private val logsWarnCount = AtomicInteger(0)
    private val unhandledExceptionCount = AtomicInteger(0)
    private val errorLogIdsCache = CacheableValue<List<String>> { errorLogIds.size }

    constructor(
        metadataService: MetadataService,
        sessionIdTracker: SessionIdTracker,
        deliveryService: DeliveryService,
        userService: UserService,
        configService: ConfigService,
        sessionProperties: EmbraceSessionProperties,
        logger: EmbLogger,
        clock: Clock,
        sessionGatingService: GatingService,
        backgroundWorker: BackgroundWorker
    ) : this(
        metadataService,
        sessionIdTracker,
        deliveryService,
        userService,
        configService,
        sessionProperties,
        logger,
        clock,
        backgroundWorker,
        sessionGatingService
    )

    @Suppress("CyclomaticComplexMethod", "ComplexMethod", "LongParameterList")
    override fun log(
        message: String,
        type: EventType,
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
        val timestamp = clock.now()
        val stacktraces = Stacktraces(
            if (stackTraceElements != null) getWrappedStackTrace(stackTraceElements) else getWrappedStackTrace(),
            customStackTrace,
            framework,
            context,
            library
        )

        // As the event is sent asynchronously and user info may change, preserve the user info
        // at the time of the log call
        val logUserInfo = userService.getUserInfo()
        backgroundWorker.submit {
            synchronized(lock) {
                if (!configService.dataCaptureEventBehavior.isLogMessageEnabled(message)) {
                    logger.logInfo("Log message disabled. Ignoring log with message $message")
                    return@submit
                }
                val id = getEmbUuid()
                if (type == EventType.INFO_LOG) {
                    logsInfoCount.incrementAndGet()
                    if (infoLogIds.size < configService.logMessageBehavior.getInfoLogLimit()) {
                        infoLogIds[timestamp] = id
                    } else {
                        logger.logInfo("Info Log limit has been reached.")
                        return@submit
                    }
                } else if (type == EventType.WARNING_LOG) {
                    logsWarnCount.incrementAndGet()
                    if (warningLogIds.size < configService.logMessageBehavior.getWarnLogLimit()) {
                        warningLogIds[timestamp] = id
                    } else {
                        logger.logInfo("Warning Log limit has been reached.")
                        return@submit
                    }
                } else if (type == EventType.ERROR_LOG) {
                    logsErrorCount.incrementAndGet()
                    if (errorLogIds.size < configService.logMessageBehavior.getErrorLogLimit()) {
                        errorLogIds[timestamp] = id
                    } else {
                        logger.logInfo("Error Log limit has been reached.")
                        return@submit
                    }
                } else {
                    logger.logWarning("Unknown log level $type")
                    return@submit
                }
                val processedMessage: String
                if (framework == AppFramework.UNITY) {
                    processedMessage = processUnityLogMessage(message)
                    if (logExceptionType == LogExceptionType.UNHANDLED) {
                        unhandledExceptionCount.incrementAndGet()
                    }
                } else if (framework == AppFramework.FLUTTER) {
                    processedMessage = processLogMessage(message)
                    if (logExceptionType == LogExceptionType.UNHANDLED) {
                        unhandledExceptionCount.incrementAndGet()
                    }
                } else {
                    processedMessage = processLogMessage(message)
                }

                // TODO validate event metadata here!
                val sessionId = sessionIdTracker.getActiveSessionId()
                val event = Event(
                    processedMessage,
                    id,
                    getEmbUuid(),
                    sessionId,
                    type,
                    clock.now(),
                    null,
                    screenshotTaken = false,
                    null,
                    metadataService.getAppState(),
                    properties,
                    sessionProperties.get(),
                    null,
                    logExceptionType.value,
                    exceptionName,
                    exceptionMessage,
                    framework.value
                )

                // Build event message
                val eventMessage = EventMessage(
                    event,
                    null,
                    metadataService.getDeviceInfo(),
                    metadataService.getAppInfo(),
                    logUserInfo,
                    null,
                    stacktraces,
                    ApiClient.MESSAGE_VERSION,
                    null
                )
                if (checkIfShouldGateLog(type)) {
                    logger.logDebug("$type was gated by config. The event was not sent.")
                    return@submit
                }

                // Sanitize log event
                val logEvent = gatingService.gateEventMessage(eventMessage)
                deliveryService.sendLog(logEvent)
            }
        }
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return findLogIds(startTime, endTime, errorLogIdsCache, errorLogIds)
    }

    private fun findLogIds(
        startTime: Long,
        endTime: Long,
        cache: CacheableValue<List<String>>,
        logIds: NavigableMap<Long, String>
    ): List<String> {
        return cache.value { ArrayList(logIds.subMap(startTime, endTime).values) }
    }

    private fun processLogMessage(
        message: String,
        maxLength: Int = configService.logMessageBehavior.getLogMessageMaximumAllowedLength()
    ): String {
        return if (message.length > maxLength) {
            val endChars = "..."

            // ensure that we never end up with a negative offset when extracting substring, regardless of the config value set
            val allowedLength = when {
                maxLength >= endChars.length -> maxLength - endChars.length
                else -> LogMessageBehavior.LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH - endChars.length
            }
            logger.logInfo("Truncating message to ${message.length} characters")
            message.substring(0, allowedLength) + endChars
        } else {
            message
        }
    }

    private fun processUnityLogMessage(message: String): String {
        return processLogMessage(message, LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH)
    }

    /**
     * Checks if the info or warning log event should be gated based on gating config. Error logs
     * should never be gated.
     *
     * @param type of the log event
     * @return true if the log should be gated
     */
    fun checkIfShouldGateLog(type: EventType?): Boolean {
        return when (type) {
            EventType.INFO_LOG -> {
                val shouldGate = configService.sessionBehavior.shouldGateInfoLog()
                shouldGate
            }

            EventType.WARNING_LOG -> {
                val shouldGate = configService.sessionBehavior.shouldGateWarnLog()
                shouldGate
            }

            else -> {
                false
            }
        }
    }

    override fun cleanCollections() {
        logsInfoCount.set(0)
        logsWarnCount.set(0)
        logsErrorCount.set(0)
        unhandledExceptionCount.set(0)
        infoLogIds.clear()
        warningLogIds.clear()
        errorLogIds.clear()
        networkLogIds.clear()
    }

    companion object {

        /**
         * The default limit of Unity log messages that can be sent.
         */
        private const val LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH = 16384

        /**
         * Gets the stack trace of the throwable.
         *
         * @return the stack trace of a throwable
         */
        @JvmStatic
        fun getWrappedStackTrace(
            stackTraceElements: Array<StackTraceElement> = Thread.currentThread().stackTrace
        ): List<String> {
            val augmentedStackReturnAddresses: MutableList<String> = ArrayList()
            for (element in stackTraceElements) {
                augmentedStackReturnAddresses.add(element.toString())
            }
            return augmentedStackReturnAddresses
        }
    }
}
