package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryService(
    private val cacheManager: DeliveryCacheManager,
    private val apiService: ApiService,
    private val gatingService: GatingService,
    private val backgroundWorker: BackgroundWorker,
    private val serializer: EmbraceSerializer,
    private val logger: InternalEmbraceLogger
) : DeliveryService {

    companion object {
        private const val TAG = "EmbraceDeliveryService"
        private const val SEND_SESSION_TIMEOUT = 1L
        private const val CRASH_TIMEOUT = 1L // Seconds to wait before timing out when sending a crash
    }

    private val backgroundActivities by lazy { mutableSetOf<String>() }

    /**
     * Caches a generated session message, with performance information generated up to the current
     * point.
     */
    override fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        val sanitizedSessionMessage = gatingService.gateSessionMessage(sessionMessage)
        cacheManager.saveSession(sanitizedSessionMessage, snapshotType)
        if (snapshotType == SessionSnapshotType.PERIODIC_CACHE) {
            return
        }

        try {
            val sessionId = sessionMessage.session.sessionId
            val action = cacheManager.loadSessionAsAction(sessionId) ?: { stream ->
                // fallback if initial caching failed for whatever reason, so we don't drop
                // the data
                ConditionalGzipOutputStream(stream).use {
                    serializer.toJson(sessionMessage, SessionMessage::class.java, it)
                }
            }
            val future = apiService.sendSession(action) {
                cacheManager.deleteSession(sessionId)
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
    override fun saveBackgroundActivity(backgroundActivityMessage: SessionMessage) {
        backgroundActivities.add(backgroundActivityMessage.session.sessionId)
        cacheManager.saveBackgroundActivity(backgroundActivityMessage)
    }

    /**
     * Caches and sends a background activity message
     *
     * @param backgroundActivityMessage    The background activity message to send
     */
    override fun sendBackgroundActivity(backgroundActivityMessage: SessionMessage) {
        logger.logDeveloper(TAG, "Sending background activity message")
        val id = backgroundActivityMessage.session.sessionId
        val action = cacheManager.saveBackgroundActivity(backgroundActivityMessage) ?: return
        sendBackgroundActivityImpl(id, action)
    }

    /**
     * Sends cached background activities messages
     */
    override fun sendBackgroundActivities() {
        logger.logDeveloper(TAG, "Sending background activity message")

        backgroundActivities.forEach { backgroundActivityId ->
            logger.logDeveloper(
                TAG,
                "Sending background activity message - background job started"
            )
            val action = cacheManager.loadBackgroundActivity(backgroundActivityId) ?: return@forEach
            sendBackgroundActivityImpl(backgroundActivityId, action)
        }
    }

    private fun sendBackgroundActivityImpl(
        backgroundActivityId: String,
        action: SerializationAction
    ) {
        try {
            apiService.sendSession(action) {
                cacheManager.deleteSession(backgroundActivityId)
            }
            logger.logDeveloper(TAG, "Session message queued to be sent.")
        } catch (ex: Exception) {
            logger.logInfo(
                "Failed to send background activity message. Embrace will " +
                    "attempt to deliver it at a future date."
            )
        }
    }

    override fun sendLog(eventMessage: EventMessage) {
        apiService.sendLog(eventMessage)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        apiService.sendNetworkCall(networkEvent)
    }

    override fun sendCrash(crash: EventMessage, processTerminating: Boolean) {
        runCatching {
            cacheManager.saveCrash(crash)
            val future = apiService.sendCrash(crash)

            if (processTerminating) {
                future.get(CRASH_TIMEOUT, TimeUnit.SECONDS)
            }
        }
    }

    override fun sendAEIBlob(blobMessage: BlobMessage) {
        apiService.sendAEIBlob(blobMessage)
    }

    override fun sendCachedSessions(ndkService: NdkService?, sessionIdTracker: SessionIdTracker) {
        sendCachedCrash()
        backgroundWorker.submit(TaskPriority.HIGH) {
            val allSessions = cacheManager.getAllCachedSessionIds()

            ndkService?.let { service ->
                val nativeCrashData = service.checkForNativeCrash()
                if (nativeCrashData != null) {
                    addCrashDataToCachedSession(nativeCrashData)
                }
            }
            sendCachedSessions(allSessions, sessionIdTracker.getActiveSessionId())
        }
    }

    private fun sendCachedCrash() {
        val crash = cacheManager.loadCrash()
        crash?.let {
            apiService.sendCrash(it)
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
                    val action = cacheManager.loadSessionAsAction(id)
                    if (action != null) {
                        apiService.sendSession(action) { cacheManager.deleteSession(id) }
                    } else {
                        logger.logError("Session $id not found")
                    }
                } catch (ex: Throwable) {
                    logger.logError("Could not send cached session", ex, true)
                }
            }
        }
    }

    override fun sendMoment(eventMessage: EventMessage) {
        apiService.sendEvent(eventMessage)
    }
}
