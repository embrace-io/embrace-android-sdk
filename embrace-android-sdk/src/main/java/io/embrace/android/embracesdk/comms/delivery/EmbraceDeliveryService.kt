package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalErrorLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.SessionMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal class EmbraceDeliveryService(
    private val cacheManager: DeliveryCacheManager,
    private val networkManager: DeliveryNetworkManager,
    private val cachedSessionsExecutorService: ExecutorService,
    private val sendSessionsExecutorService: ExecutorService,
    private val logger: InternalEmbraceLogger,
    private val configService: ConfigService,
) : DeliveryService, DeliveryServiceNetwork by networkManager {

    companion object {
        private const val TAG = "EmbraceDeliveryService"

        private const val SEND_SESSION_TIMEOUT = 1L
        private const val CRASH_MAX_DIFF_WITH_SESSION_END = 7000
    }

    private val backgroundActivities by lazy { mutableSetOf<String>() }

    /**
     * Caches a generated session message, with performance information generated up to the current
     * point.
     */
    override fun saveSession(sessionMessage: SessionMessage) {
        cacheManager.saveSession(sessionMessage)
    }

    /**
     * Caches and sends over the network a session message
     *
     * @param sessionMessage    The session message to send
     * @param state             Whether this message is for the session start or end
     */
    override fun sendSession(sessionMessage: SessionMessage, state: SessionMessageState) {
        logger.logDeveloper(TAG, "Sending session message")

        sendSessionsExecutorService.submit {
            logger.logDeveloper(TAG, "Sending session message - background job started")
            val sessionBytes = cacheManager.saveSession(sessionMessage)

            sessionBytes?.also { session ->
                logger.logDeveloper(TAG, "Serialized session message ready to be sent")

                try {
                    var onFinish: (() -> Unit)? = null
                    if (state == SessionMessageState.END || state == SessionMessageState.END_WITH_CRASH) {
                        onFinish = { cacheManager.deleteSession(sessionMessage.session.sessionId) }
                        if (configService.sdkModeBehavior.isIntegrationModeEnabled()) {
                            validateNetworkCalls(sessionMessage)
                            validateSessionTimestamps(sessionMessage)
                        }
                    }

                    if (state == SessionMessageState.END_WITH_CRASH) {
                        // perform session request synchronously
                        networkManager.sendSession(
                            session,
                            onFinish
                        )[SEND_SESSION_TIMEOUT, TimeUnit.SECONDS]
                        logger.logDeveloper(TAG, "Session message sent.")
                    } else {
                        // perform session request asynchronously
                        networkManager.sendSession(session, onFinish)
                        logger.logDeveloper(TAG, "Session message queued to be sent.")
                    }
                    logger.logDeveloper(
                        TAG,
                        "Current session has been successfully removed from cache."
                    )
                } catch (ex: Exception) {
                    logger.logInfo(
                        "Failed to send session end message. Embrace will store the " +
                            "session message and attempt to deliver it at a future date."
                    )
                }
            }
        }
    }

    /**
     * Caches a background activity message
     *
     * @param backgroundActivityMessage    The background activity message to cache
     */
    override fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage) {
        backgroundActivities.add(backgroundActivityMessage.backgroundActivity.sessionId)
        cacheManager.saveBackgroundActivity(backgroundActivityMessage)
    }

    /**
     * Caches and sends a background activity message
     *
     * @param backgroundActivityMessage    The background activity message to send
     */
    override fun sendBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage) {
        logger.logDeveloper(TAG, "Sending background activity message")

        sendSessionsExecutorService.submit {
            logger.logDeveloper(TAG, "Sending background activity message - background job started")
            val baBytes = cacheManager.saveBackgroundActivity(backgroundActivityMessage)

            baBytes?.also { backgroundActivity ->
                logger.logDeveloper(TAG, "Serialized session message ready to be sent")

                try {
                    val onFinish: (() -> Unit) =
                        { cacheManager.deleteSession(backgroundActivityMessage.backgroundActivity.sessionId) }
                    networkManager.sendSession(backgroundActivity, onFinish)
                    logger.logDeveloper(TAG, "Session message queued to be sent.")
                } catch (ex: Exception) {
                    logger.logInfo(
                        "Failed to send background activity message. Embrace will " +
                            "attempt to deliver it at a future date."
                    )
                }
            }
        }
    }

    /**
     * Sends cached background activities messages
     *
     */
    override fun sendBackgroundActivities() {
        logger.logDeveloper(TAG, "Sending background activity message")

        sendSessionsExecutorService.submit {
            backgroundActivities.forEach { backgroundActivityId ->
                logger.logDeveloper(TAG, "Sending background activity message - background job started")
                val baBytes = cacheManager.loadBackgroundActivity(backgroundActivityId)

                baBytes?.also { backgroundActivity ->
                    logger.logDeveloper(TAG, "Serialized session message ready to be sent")

                    try {
                        val onFinish: () -> Unit = { cacheManager.deleteSession(backgroundActivityId) }
                        networkManager.sendSession(backgroundActivity, onFinish)
                        logger.logDeveloper(TAG, "Session message queued to be sent.")
                    } catch (ex: Exception) {
                        logger.logInfo(
                            "Failed to send background activity message. Embrace will " +
                                "attempt to deliver it at a future date."
                        )
                    }
                }
            }
        }
    }

    private fun validateSessionTimestamps(sessionMessage: SessionMessage) {
        val endTime = sessionMessage.session.endTime ?: 0
        if (endTime <= sessionMessage.session.startTime) {
            logger.logError(
                "Session end time less or equal to start time",
                InternalErrorLogger.IntegrationModeException("wrong session start/end time")
            )
        }
    }

    private fun validateNetworkCalls(sessionMessage: SessionMessage) {
        val p = sessionMessage.performanceInfo
        val networkRequests = p?.networkRequests
        if (networkRequests?.networkSessionV2?.requestCounts?.isEmpty() == true) {
            logger.logError(
                "Session with no network calls",
                InternalErrorLogger.IntegrationModeException("No network calls")
            )
        }
    }

    override fun sendCachedSessions(
        isNdkEnabled: Boolean,
        ndkService: NdkService,
        currentSession: String?
    ) {
        sendCachedCrash()
        if (isNdkEnabled) {
            sendCachedSessionsWithNdk(ndkService, currentSession)
        } else {
            sendCachedSessionsWithoutNdk(currentSession)
        }
    }

    /**
     * Persist crash to disk so it can be sent on the next SDK start.
     */
    override fun saveCrash(crash: EventMessage) {
        cacheManager.saveCrash(crash)
    }

    private fun sendCachedCrash() {
        val crash = cacheManager.loadCrash()
        crash?.let {
            networkManager.sendCrash(it)
        }
    }

    private fun sendCachedSessionsWithoutNdk(currentSession: String?) {
        cachedSessionsExecutorService.submit {
            sendCachedSessions(cacheManager.getAllCachedSessionIds(), currentSession)
        }
    }

    private fun sendCachedSessionsWithNdk(ndkService: NdkService, currentSession: String?) {
        cachedSessionsExecutorService.submit {
            val allSessions = cacheManager.getAllCachedSessionIds()
            logger.logDeveloper(TAG, "NDK enabled, checking for native crashes")
            val nativeCrashData = ndkService.checkForNativeCrash()
            if (nativeCrashData != null) {
                addCrashDataToCachedSession(nativeCrashData)
            }
            sendCachedSessions(allSessions, currentSession)
        }
    }

    private fun addCrashDataToCachedSession(nativeCrashData: NativeCrashData) {
        cacheManager.loadSession(nativeCrashData.sessionId)
            ?.also { sessionMessage ->
                // Create a new session message with the specified crash id
                val newSessionMessage =
                    attachCrashToSession(nativeCrashData, sessionMessage)
                // Replace the cached file for the corresponding session
                cacheManager.saveSession(newSessionMessage)
            } ?: run {
            logger.logError(
                "Could not find session with id ${nativeCrashData.sessionId} to " +
                    "add native crash"
            )
        }
    }

    private fun attachCrashToSession(
        nativeCrashData: NativeCrashData,
        sessionMessage: SessionMessage
    ): SessionMessage {
        logger.logDeveloper(
            TAG,
            "Attaching native crash ${nativeCrashData.nativeCrashId} to session ${sessionMessage.session.sessionId}"
        )

        if (configService.sdkModeBehavior.isIntegrationModeEnabled()) {
            verifyCrashTimeStamp(nativeCrashData, sessionMessage)
        }

        val session = sessionMessage.session.copy(crashReportId = nativeCrashData.nativeCrashId)
        return sessionMessage.copy(session = session)
    }

    // Crash must occur within 7 seconds of the session end
    private fun verifyCrashTimeStamp(
        nativeCrashData: NativeCrashData,
        sessionMessage: SessionMessage
    ) {
        val endTime = sessionMessage.session.endTime ?: 0
        if (abs(nativeCrashData.timestamp - endTime) >= CRASH_MAX_DIFF_WITH_SESSION_END) {
            logger.logError(
                "Crash " + nativeCrashData.nativeCrashId + " happened outside 7 seconds of session end",
                InternalErrorLogger.IntegrationModeException(nativeCrashData.nativeCrashId + " outside 7 secs range")
            )
        }
    }

    private fun sendCachedSessions(ids: List<String>, currentSession: String?) {
        ids.forEach { id ->
            if (id != currentSession) {
                try {
                    val payload = cacheManager.loadSessionBytes(id)
                    if (payload != null) {
                        if (configService.sdkModeBehavior.isIntegrationModeEnabled()) {
                            logger.logError(
                                "send cached sessions",
                                InternalErrorLogger.IntegrationModeException("Found cached session $id")
                            )
                        }

                        // The network requests will be executed sequentially in a single-threaded executor by the network manager
                        networkManager.sendSession(payload) { cacheManager.deleteSession(id) }
                    } else {
                        logger.logError("Session $id not found")
                    }
                } catch (ex: Exception) {
                    logger.logError("Could not send cached session $id")
                }
            }
        }
    }

    override fun sendEventAsync(eventMessage: EventMessage) {
        sendSessionsExecutorService.submit {
            networkManager.sendEvent(eventMessage)
        }
    }
}
