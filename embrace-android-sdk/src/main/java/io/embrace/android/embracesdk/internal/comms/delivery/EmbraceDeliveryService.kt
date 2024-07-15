package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionId
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.payload.toFailedSpan
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.io.OutputStream
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
    override fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType) {
        cacheManager.saveSession(envelope, snapshotType)
        if (snapshotType == SessionSnapshotType.PERIODIC_CACHE) {
            return
        }

        try {
            val sessionId = envelope.getSessionId() ?: return
            val action: SerializationAction = cacheManager.loadSessionAsAction(sessionId)
                ?: { stream: OutputStream ->
                    // fallback if initial caching failed for whatever reason, so we don't drop
                    // the data
                    ConditionalGzipOutputStream(stream).use<ConditionalGzipOutputStream, Unit> {
                        serializer.toJson(envelope, Envelope.sessionEnvelopeType)
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
                    val completedSpanIds = data.spans?.map { it.spanId }?.toSet() ?: emptySet()
                    val spansToFail = data.spanSnapshots
                        ?.filterNot { completedSpanIds.contains(it.spanId) }
                        ?.map { it.toFailedSpan(endTimeMs = getFailedSpanEndTimeMs(this)) }
                        ?: emptyList()
                    val completedSpans = (data.spans ?: emptyList()) + spansToFail
                    copy(
                        data = data.copy(
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

    private fun Envelope<SessionPayload>.attachCrashToSession(nativeCrashData: NativeCrashData): Envelope<SessionPayload> {
        val spans = data.spans ?: return this
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

    private fun getFailedSpanEndTimeMs(envelope: Envelope<SessionPayload>) =
        envelope.getSessionSpan()?.endTimeNanos?.nanosToMillis() ?: -1

    override fun sendMoment(eventMessage: EventMessage) {
        apiService.sendEvent(eventMessage)
    }
}
