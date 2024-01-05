package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.Stacktraces
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import java.sql.Timestamp
import java.util.NavigableMap
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Logs messages remotely, so that they can be viewed as events during a user's session.
 */
internal class EmbraceLogMessageService constructor(
    private val metadataService: MetadataService,
    private val deliveryService: DeliveryService,
    private val userService: UserService,
    private val configService: ConfigService,
    private val sessionProperties: EmbraceSessionProperties,
    private val logger: InternalEmbraceLogger,
    private val clock: Clock,
    private val executorService: ExecutorService,
    private val gatingService: GatingService,
    private val networkConnectivityService: NetworkConnectivityService
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
    private val infoLogIdsCache = CacheableValue<List<String>> { infoLogIds.size }
    private val warningLogIdsCache = CacheableValue<List<String>> { warningLogIds.size }
    private val errorLogIdsCache = CacheableValue<List<String>> { errorLogIds.size }
    private val networkLogIdsCache = CacheableValue<List<String>> { networkLogIds.size }

    constructor(
        metadataService: MetadataService,
        deliveryService: DeliveryService,
        userService: UserService,
        configService: ConfigService,
        sessionProperties: EmbraceSessionProperties,
        logger: InternalEmbraceLogger,
        clock: Clock,
        sessionGatingService: GatingService,
        networkConnectivityService: NetworkConnectivityService,
        executorService: ExecutorService
    ) : this(
        metadataService,
        deliveryService,
        userService,
        configService,
        sessionProperties,
        logger,
        clock,
        executorService,
        sessionGatingService,
        networkConnectivityService
    )

    override fun logNetwork(networkCaptureCall: NetworkCapturedCall?) {
        val networkEventTimestamp = clock.now()
        if (networkCaptureCall == null) {
            logDebug("NetworkCaptureCall is null, nothing to log")
            return
        }
        try {
            logDeveloper("EmbraceRemoteLogger", "Attempting to log network data")
            executorService.submit<Any?>(
                Callable<Any?> {
                    synchronized(lock) {
                        val id = getEmbUuid()
                        networkLogIds[networkEventTimestamp] = id
                        val optionalSessionId = metadataService.activeSessionId
                        val networkEvent = NetworkEvent(
                            metadataService.getAppId(),
                            metadataService.getAppInfo(),
                            metadataService.getDeviceId(),
                            id,
                            networkCaptureCall,
                            Timestamp(networkEventTimestamp).toString(),
                            networkConnectivityService.ipAddress,
                            optionalSessionId
                        )
                        logDeveloper("EmbraceRemoteLogger", "Attempt to Send NETWORK Event")
                        deliveryService.sendNetworkCall(networkEvent)
                        logDeveloper(
                            "EmbraceRemoteLogger",
                            "LogNetwork api call running in background job"
                        )
                    }
                    null
                }
            )
        } catch (ex: Exception) {
            logDebug("Failed to log network call using Embrace SDK.", ex)
        }
    }

    override fun log(
        message: String,
        type: EmbraceEvent.Type,
        properties: Map<String, Any>?
    ) {
        log(
            message,
            type,
            LogExceptionType.NONE,
            properties,
            null,
            null,
            AppFramework.NATIVE,
            null,
            null,
            null,
            null
        )
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod", "LongParameterList")
    override fun log(
        message: String,
        type: EmbraceEvent.Type,
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
        logDeveloper("EmbraceRemoteLogger", "Attempting to log")
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
        logDeveloper("EmbraceRemoteLogger", "Added user info to log")
        executorService.submit<Any?>(
            Callable<Any?> {
                synchronized(lock) {
                    if (!configService.dataCaptureEventBehavior.isLogMessageEnabled(message)) {
                        logger.logWarning("Log message disabled. Ignoring log with message $message")
                        return@Callable null
                    }
                    if (!configService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.LOG)) {
                        logger.logWarning("Log message disabled. Ignoring all Logs.")
                        return@Callable null
                    }
                    val id = getEmbUuid()
                    if (type == EmbraceEvent.Type.INFO_LOG) {
                        logDeveloper("EmbraceRemoteLogger", "New INFO log")
                        logsInfoCount.incrementAndGet()
                        if (infoLogIds.size < configService.logMessageBehavior.getInfoLogLimit()) {
                            logDeveloper(
                                "EmbraceRemoteLogger",
                                "Logging INFO log number $logsInfoCount"
                            )
                            infoLogIds[timestamp] = id
                        } else {
                            logger.logWarning("Info Log limit has been reached.")
                            return@Callable null
                        }
                    } else if (type == EmbraceEvent.Type.WARNING_LOG) {
                        logsWarnCount.incrementAndGet()
                        if (warningLogIds.size < configService.logMessageBehavior.getWarnLogLimit()) {
                            logDeveloper(
                                "EmbraceRemoteLogger",
                                "Logging WARNING log number $logsWarnCount"
                            )
                            warningLogIds[timestamp] = id
                        } else {
                            logger.logWarning("Warning Log limit has been reached.")
                            return@Callable null
                        }
                    } else if (type == EmbraceEvent.Type.ERROR_LOG) {
                        logsErrorCount.incrementAndGet()
                        if (errorLogIds.size < configService.logMessageBehavior.getErrorLogLimit()) {
                            logDeveloper(
                                "EmbraceRemoteLogger",
                                "Logging ERROR log number $logsErrorCount"
                            )
                            errorLogIds[timestamp] = id
                        } else {
                            logger.logWarning("Error Log limit has been reached.")
                            return@Callable null
                        }
                    } else {
                        logger.logWarning("Unknown log level $type")
                        return@Callable null
                    }
                    val processedMessage: String
                    if (framework == AppFramework.UNITY) {
                        logDeveloper("EmbraceRemoteLogger", "Process Unity Log message")
                        processedMessage = processUnityLogMessage(message)
                        if (logExceptionType == LogExceptionType.UNHANDLED) {
                            unhandledExceptionCount.incrementAndGet()
                        }
                    } else if (framework == AppFramework.FLUTTER) {
                        logDeveloper("EmbraceRemoteLogger", "Process Flutter Log message")
                        processedMessage = processLogMessage(message)
                        if (logExceptionType == LogExceptionType.UNHANDLED) {
                            unhandledExceptionCount.incrementAndGet()
                        }
                    } else {
                        logDeveloper("EmbraceRemoteLogger", "Process simple Log message")
                        processedMessage = processLogMessage(message)
                    }

                    // TODO validate event metadata here!
                    var sessionId: String? = null
                    val optionalSessionId = metadataService.activeSessionId
                    if (optionalSessionId != null) {
                        logDeveloper("EmbraceRemoteLogger", "Adding SessionId to event")
                        sessionId = optionalSessionId
                    }
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
                        logger.logDebug("$type was gated by config. The event wasnot sent.")
                        return@Callable null
                    }

                    // Sanitize log event
                    val logEvent = gatingService.gateEventMessage(eventMessage)
                    logDeveloper("EmbraceRemoteLogger", "Attempt to Send log Event")
                    deliveryService.sendLog(logEvent)
                    logDeveloper("EmbraceRemoteLogger", "LogEvent api call running in background job")
                }
                null
            }
        )
    }

    override fun findInfoLogIds(startTime: Long, endTime: Long): List<String> {
        return findLogIds(startTime, endTime, infoLogIdsCache, infoLogIds)
    }

    override fun findWarningLogIds(startTime: Long, endTime: Long): List<String> {
        return findLogIds(startTime, endTime, warningLogIdsCache, warningLogIds)
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        return findLogIds(startTime, endTime, errorLogIdsCache, errorLogIds)
    }

    override fun findNetworkLogIds(startTime: Long, endTime: Long): List<String> {
        return findLogIds(startTime, endTime, networkLogIdsCache, networkLogIds)
    }

    private fun findLogIds(
        startTime: Long,
        endTime: Long,
        cache: CacheableValue<List<String>>,
        logIds: NavigableMap<Long, String>
    ): List<String> {
        return cache.value { ArrayList(logIds.subMap(startTime, endTime).values) }
    }

    override fun getInfoLogsAttemptedToSend(): Int = logsInfoCount.get()

    override fun getWarnLogsAttemptedToSend(): Int = logsWarnCount.get()

    override fun getErrorLogsAttemptedToSend(): Int = logsErrorCount.get()

    override fun getUnhandledExceptionsSent(): Int {
        if (unhandledExceptionCount.get() > 0) {
            logDeveloper(
                "EmbraceRemoteLogger",
                "UnhandledException number: $unhandledExceptionCount"
            )
        }
        return unhandledExceptionCount.get()
    }

    private fun processLogMessage(
        message: String,
        maxLength: Int = configService.logMessageBehavior.getLogMessageMaximumAllowedLength()
    ): String {
        return if (message.length > maxLength) {
            logDeveloper(
                "EmbraceRemoteLogger",
                "Message length exceeds the allowed max length"
            )
            val endChars = "..."

            // ensure that we never end up with a negative offset when extracting substring, regardless of the config value set
            val allowedLength = when {
                maxLength >= endChars.length -> maxLength - endChars.length
                else -> LogMessageBehavior.LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH - endChars.length
            }
            logger.logWarning("Truncating message to ${message.length} characters")
            message.substring(0, allowedLength) + endChars
        } else {
            logDeveloper("EmbraceRemoteLogger", "Allowed message length")
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
    fun checkIfShouldGateLog(type: EmbraceEvent.Type?): Boolean {
        return when (type) {
            EmbraceEvent.Type.INFO_LOG -> {
                val shouldGate = configService.sessionBehavior.shouldGateInfoLog()
                logDeveloper(
                    "EmbraceRemoteLogger",
                    "Should gate INFO log: $shouldGate"
                )
                shouldGate
            }

            EmbraceEvent.Type.WARNING_LOG -> {
                val shouldGate = configService.sessionBehavior.shouldGateWarnLog()
                logDeveloper(
                    "EmbraceRemoteLogger",
                    "Should gate WARN log: $shouldGate"
                )
                shouldGate
            }

            else -> {
                logDeveloper(
                    "EmbraceRemoteLogger",
                    "Should gate log: false"
                )
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
        logDeveloper("EmbraceRemoteLogger", "Collections cleaned")
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
            logDeveloper("EmbraceRemoteLogger", "Processing wrapped stack trace")
            val augmentedStackReturnAddresses: MutableList<String> = ArrayList()
            for (element in stackTraceElements) {
                augmentedStackReturnAddresses.add(element.toString())
            }
            return augmentedStackReturnAddresses
        }
    }
}
