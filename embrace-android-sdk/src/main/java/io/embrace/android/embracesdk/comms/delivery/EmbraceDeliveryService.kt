package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.toFailedSpan
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.ndk.NativeCrashService
import io.embrace.android.embracesdk.opentelemetry.embCrashId
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.getSessionId
import io.embrace.android.embracesdk.payload.getSessionSpan
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
    private val logger: EmbLogger
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
            val sessionId = sessionMessage.getSessionId() ?: return
            val action = cacheManager.loadSessionAsAction(sessionId) ?: { stream ->
                // fallback if initial caching failed for whatever reason, so we don't drop
                // the data
                ConditionalGzipOutputStream(stream).use {
                    serializer.toJson(sessionMessage, SessionMessage::class.java, it)
                }
            }
            val future = apiService.sendSession(action) { successful ->
                if (!successful) {
                    val message =
                        "Session deleted without request being sent: ID $sessionId"
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

    override fun sendCachedSessions(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        sessionIdTracker: SessionIdTracker
    ) {
        sendCachedCrash()
        backgroundWorker.submit(TaskPriority.HIGH) {
            val allSessions = cacheManager.getAllCachedSessionIds().filter {
                it.sessionId != sessionIdTracker.getActiveSessionId()
            }

            allSessions.map { it.sessionId }.forEach { sessionId ->
                cacheManager.transformSession(sessionId = sessionId) {
                    val completedSpanIds = data?.spans?.map { it.spanId }?.toSet() ?: emptySet()
                    val spansToFail = data?.spanSnapshots
                        ?.filterNot { completedSpanIds.contains(it.spanId) }
                        ?.map { it.toFailedSpan(endTimeMs = getFailedSpanEndTimeMs(this)) }
                        ?: emptyList()
                    val completedSpans = (data?.spans ?: emptyList()) + spansToFail
                    copy(
                        data = data?.copy(
                            spans = completedSpans,
                            spanSnapshots = emptyList(),
                        )
                    )
                }
            }

            nativeCrashServiceProvider()?.let { service ->
                val nativeCrashData = service.getAndSendNativeCrash()
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
        cacheManager.transformSession(nativeCrashData.sessionId) {
            attachCrashToSession(nativeCrashData)
        }
    }

    private fun SessionMessage.attachCrashToSession(nativeCrashData: NativeCrashData): SessionMessage {
        val spans = data?.spans ?: return this
        val sessionSpan = getSessionSpan() ?: return this

        val alteredSessionSpan = sessionSpan.copy(
            attributes = sessionSpan.attributes?.plus(Attribute(embCrashId.name, nativeCrashData.nativeCrashId))
        )
        return copy(
            data = data.copy(
                spans = spans.minus(sessionSpan).plus(alteredSessionSpan)
            )
        )
    }

    private fun sendCachedSessions(cachedSessions: List<CachedSession>) {
        cachedSessions.forEach { cachedSession ->
            try {
                val sessionId = cachedSession.sessionId
                val action = cacheManager.loadSessionAsAction(sessionId)
                if (action != null) {
                    apiService.sendSession(action) { successful ->
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
                logger.logError("Could not send cached session", ex)
            }
        }
    }

    private fun getFailedSpanEndTimeMs(sessionMessage: SessionMessage) =
        sessionMessage.getSessionSpan()?.endTimeNanos?.nanosToMillis() ?: -1

    override fun sendMoment(eventMessage: EventMessage) {
        apiService.sendEvent(eventMessage)
    }
}
