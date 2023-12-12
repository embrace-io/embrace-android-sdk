package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryService(
    private val cacheManager: DeliveryCacheManager,
    private val apiService: ApiService,
    private val gatingService: GatingService,
    private val cachedSessionsExecutorService: ExecutorService,
    private val sendSessionsExecutorService: ExecutorService,
    private val logger: InternalEmbraceLogger
) : DeliveryService {

    companion object {
        private const val TAG = "EmbraceDeliveryService"

        private const val SEND_SESSION_TIMEOUT = 1L
    }

    private val backgroundActivities by lazy { mutableSetOf<String>() }

    /**
     * Caches a generated session message, with performance information generated up to the current
     * point.
     */
    override fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        val sanitizedSessionMessage = gatingService.gateSessionMessage(sessionMessage)
        val sessionBytes = cacheManager.saveSession(sanitizedSessionMessage, snapshotType)
        if (sessionBytes == null || snapshotType == SessionSnapshotType.PERIODIC_CACHE) {
            return
        }

        try {
            val future = apiService.sendSession(sessionBytes) {
                cacheManager.deleteSession(sessionMessage.session.sessionId)
            }
            if (snapshotType == SessionSnapshotType.JVM_CRASH) {
                future?.get(SEND_SESSION_TIMEOUT, TimeUnit.SECONDS)
            }
        } catch (ex: Exception) {
            logger.logInfo(
                "Failed to send session end message. Embrace will store the " +
                    "session message and attempt to deliver it at a future date."
            )
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
                    apiService.sendSession(backgroundActivity, onFinish)
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
                        apiService.sendSession(backgroundActivity, onFinish)
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

    override fun sendLog(eventMessage: EventMessage) {
        apiService.sendLog(eventMessage)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        apiService.sendNetworkCall(networkEvent)
    }

    override fun sendCrash(crash: EventMessage) {
        cacheManager.saveCrash(crash)
        apiService.sendCrash(crash)
    }

    override fun sendAEIBlob(blobMessage: BlobMessage) {
        apiService.sendAEIBlob(blobMessage)
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

    private fun sendCachedCrash() {
        val crash = cacheManager.loadCrash()
        crash?.let {
            apiService.sendCrash(it)
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
                cacheManager.saveSession(newSessionMessage, SessionSnapshotType.NORMAL_END)
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

        val session = sessionMessage.session.copy(crashReportId = nativeCrashData.nativeCrashId)
        return sessionMessage.copy(session = session)
    }

    private fun sendCachedSessions(ids: List<String>, currentSession: String?) {
        ids.forEach { id ->
            if (id != currentSession) {
                try {
                    val payload = cacheManager.loadSessionBytes(id)
                    if (payload != null) {
                        // The network requests will be executed sequentially in a single-threaded executor by the network manager
                        apiService.sendSession(payload) { cacheManager.deleteSession(id) }
                    } else {
                        logger.logError("Session $id not found")
                    }
                } catch (ex: Throwable) {
                    logger.logError("Could not send cached session", ex, true)
                }
            }
        }
    }

    override fun sendEventAsync(eventMessage: EventMessage) {
        sendSessionsExecutorService.submit {
            apiService.sendEvent(eventMessage)
        }
    }

    override fun sendEventAndWait(eventMessage: EventMessage) {
        apiService.sendEventAndWait(eventMessage)
    }
}
