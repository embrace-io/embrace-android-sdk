package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.toFailedSpan
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.isV2Payload
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryService(
    private val cacheManager: DeliveryCacheManager,
    private val apiService: ApiService,
    private val backgroundWorker: BackgroundWorker,
    private val serializer: PlatformSerializer,
    private val logger: InternalEmbraceLogger
) : DeliveryService {

    companion object {
        private const val SEND_SESSION_TIMEOUT = 1L
        private const val CRASH_TIMEOUT = 1L // Seconds to wait before timing out when sending a crash
    }

    /**
     * Caches a generated session message, with performance information generated up to the current
     * point.
     */
    override fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        cacheManager.saveSession(sessionMessage, snapshotType)
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
            val future = apiService.sendSession(sessionMessage.isV2Payload(), action) { successful ->
                if (!successful) {
                    val message =
                        "Session deleted without request being sent: ID $sessionId, timestamp ${sessionMessage.session.startTime}"
                    logger.logWarning(message, SessionPurgeException(message))
                }
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

    override fun sendLog(eventMessage: EventMessage) {
        apiService.sendLog(eventMessage)
    }

    override fun sendLogs(logEnvelope: Envelope<LogPayload>) {
        apiService.sendLogEnvelope(logEnvelope)
    }

    override fun saveLogs(logEnvelope: Envelope<LogPayload>) {
        apiService.saveLogEnvelope(logEnvelope)
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

    override fun sendCachedSessions(ndkService: NdkService?, sessionIdTracker: SessionIdTracker) {
        sendCachedCrash()
        backgroundWorker.submit(TaskPriority.HIGH) {
            val allSessions = cacheManager.getAllCachedSessionIds().filter {
                it.sessionId != sessionIdTracker.getActiveSessionId()
            }

            allSessions.map { it.sessionId }.forEach { sessionId ->
                cacheManager.transformSession(sessionId = sessionId) { sessionMessage ->
                    val completedSpanIds = sessionMessage.spans?.map { it.spanId }?.toSet() ?: emptySet()
                    val spansToFail = sessionMessage.spanSnapshots
                        ?.filterNot { completedSpanIds.contains(it.spanId) }
                        ?.map { it.toFailedSpan(sessionMessage.session.endTime ?: 0L) }
                        ?: emptyList()
                    val completedSpans = (sessionMessage.spans ?: emptyList()) + spansToFail
                    sessionMessage.copy(spans = completedSpans, spanSnapshots = emptyList())
                }
            }

            ndkService?.let { service ->
                val nativeCrashData = service.checkForNativeCrash()
                if (nativeCrashData != null) {
                    addCrashDataToCachedSession(nativeCrashData)
                }
            }
            sendCachedSessions(allSessions)
        }
    }

    private fun sendCachedCrash() {
        val crash = cacheManager.loadCrash()
        crash?.let {
            apiService.sendCrash(it)
        }
    }

    private fun addCrashDataToCachedSession(nativeCrashData: NativeCrashData) {
        cacheManager.transformSession(nativeCrashData.sessionId) { sessionMessage ->
            attachCrashToSession(nativeCrashData, sessionMessage)
        }
    }

    private fun attachCrashToSession(
        nativeCrashData: NativeCrashData,
        sessionMessage: SessionMessage
    ): SessionMessage {
        val session = sessionMessage.session.copy(crashReportId = nativeCrashData.nativeCrashId)
        return sessionMessage.copy(session = session)
    }

    private fun sendCachedSessions(cachedSessions: List<CachedSession>) {
        cachedSessions.forEach { cachedSession ->
            try {
                val sessionId = cachedSession.sessionId
                val action = cacheManager.loadSessionAsAction(sessionId)
                if (action != null) {
                    // temporarily assume all sessions are v1. Future changeset
                    // will encode this information in the filename.
                    apiService.sendSession(false, action) { successful ->
                        if (!successful) {
                            val message = "Cached session deleted without request being sent. File name: ${cachedSession.filename}"
                            logger.logWarning(message, SessionPurgeException(message))
                        }
                        cacheManager.deleteSession(sessionId)
                    }
                } else {
                    logger.logError("Session $sessionId not found")
                }
            } catch (ex: Throwable) {
                logger.logError("Could not send cached session", ex, true)
            }
        }
    }

    override fun sendMoment(eventMessage: EventMessage) {
        apiService.sendEvent(eventMessage)
    }
}
