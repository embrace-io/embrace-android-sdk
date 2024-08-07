package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryCacheManager.Companion.PENDING_API_CALLS_FILE_NAME
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionId
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import java.io.Closeable

internal class EmbraceDeliveryCacheManager(
    private val cacheService: CacheService,
    private val backgroundWorker: BackgroundWorker,
    private val logger: EmbLogger
) : Closeable, DeliveryCacheManager {

    companion object {
        /**
         * File name to cache JVM crash information
         */
        private const val CRASH_FILE_NAME = "crash.json"

        /**
         * File name for pending api calls
         */
        private const val PENDING_API_CALLS_FILE_NAME = "failed_api_calls.json"

        const val MAX_SESSIONS_CACHED = 64
    }

    // The session id is used as key for this map
    // This list is initialized when getAllCachedSessions() is called.
    private val cachedSessions = mutableMapOf<String, CachedSession>()

    override fun saveSession(
        envelope: Envelope<SessionPayload>,
        snapshotType: SessionSnapshotType
    ) {
        try {
            if (cachedSessions.size >= MAX_SESSIONS_CACHED) {
                deleteOldestSessions()
            }
            val span = envelope.getSessionSpan()
            val sessionId = envelope.getSessionId() ?: return
            val sessionStartTimeMs = span?.startTimeNanos?.nanosToMillis() ?: return
            val writeSync = snapshotType == SessionSnapshotType.JVM_CRASH
            val snapshot = snapshotType == SessionSnapshotType.PERIODIC_CACHE

            saveSessionBytes(
                sessionId,
                sessionStartTimeMs,
                writeSync,
                snapshot
            ) { filename: String ->
                Systrace.traceSynchronous("serialize-session") {
                    cacheService.writeSession(filename, envelope)
                }
            }
        } catch (exc: Throwable) {
            logger.logError("Save session failed", exc)
            throw exc
        }
    }

    override fun loadSessionAsAction(sessionId: String): SerializationAction? {
        cachedSessions[sessionId]?.let { cachedSession ->
            return loadPayloadAsAction(cachedSession.filename)
        }
        logger.logError("Session $sessionId is not in cache")
        return null
    }

    override fun deleteSession(sessionId: String) {
        cachedSessions[sessionId]?.let { cachedSession ->
            backgroundWorker.submit {
                try {
                    cacheService.deleteFile(cachedSession.filename)
                    cachedSessions.remove(sessionId)
                } catch (ex: Exception) {
                    logger.logError("Could not remove session from cache: $sessionId")
                }
            }
        }
    }

    /**
     * This method will do disk reads so do not run it on the main thread
     */
    override fun getAllCachedSessionIds(): List<CachedSession> {
        val sessionFileIds = cacheService.normalizeCacheAndGetSessionFileIds()
        sessionFileIds.forEach { filename ->
            CachedSession.fromFilename(filename)?.let { cachedSession ->
                cachedSessions[cachedSession.sessionId] = cachedSession
            } ?: logger.logError("Unrecognized cached file: $filename")
        }

        return cachedSessions.values.toList()
    }

    override fun saveCrash(crash: EventMessage) {
        cacheService.cacheObject(CRASH_FILE_NAME, crash, EventMessage::class.java)
    }

    override fun loadCrash(): EventMessage? {
        return cacheService.loadObject(CRASH_FILE_NAME, EventMessage::class.java)
    }

    override fun deleteCrash() {
        cacheService.deleteFile(CRASH_FILE_NAME)
    }

    override fun savePayload(action: SerializationAction): String {
        val name = "payload_" + Uuid.getEmbUuid()
        backgroundWorker.submit {
            cacheService.cachePayload(name, action)
        }
        return name
    }

    override fun loadPayloadAsAction(name: String): SerializationAction {
        return cacheService.loadPayload(name)
    }

    override fun deletePayload(name: String) {
        backgroundWorker.submit {
            cacheService.deleteFile(name)
        }
    }

    /**
     * Saves the [PendingApiCalls] map to a file named [PENDING_API_CALLS_FILE_NAME].
     */
    override fun savePendingApiCalls(pendingApiCalls: PendingApiCalls, sync: Boolean) {
        if (sync) {
            cacheService.cacheObject(PENDING_API_CALLS_FILE_NAME, pendingApiCalls, PendingApiCalls::class.java)
        } else {
            backgroundWorker.submit {
                cacheService.cacheObject(
                    PENDING_API_CALLS_FILE_NAME,
                    pendingApiCalls,
                    PendingApiCalls::class.java
                )
            }
        }
    }

    /**
     * Loads the [PendingApiCalls] map from a file named [PENDING_API_CALLS_FILE_NAME].
     * If loadObject returns null, it tries to load the old version of the file which was storing
     * a list of [PendingApiCall] instead of [PendingApiCalls].
     */
    override fun loadPendingApiCalls(): PendingApiCalls =
        runCatching {
            cacheService.loadObject<PendingApiCalls>(PENDING_API_CALLS_FILE_NAME, PendingApiCalls::class.java)
        }.getOrNull()
            ?: loadPendingApiCallsOldVersion()
            ?: PendingApiCalls()

    /**
     * The caller of this method needs to be run in the [WorkerName.DELIVERY_CACHE] thread so all session writes are done serially
     */
    override fun transformSession(
        sessionId: String,
        transformer: (Envelope<SessionPayload>) -> Envelope<SessionPayload>
    ) {
        val filename = cachedSessions[sessionId]?.filename ?: return
        cacheService.transformSession(filename, transformer)
    }

    /**
     * Loads the old version of the [PENDING_API_CALLS_FILE_NAME] file where
     * it was storing a list of [PendingApiCall] instead of [PendingApiCalls]
     */
    private fun loadPendingApiCallsOldVersion(): PendingApiCalls? {
        var cachedApiCallsPerEndpoint: PendingApiCalls? = null
        val loadPendingApiCallsQueue = runCatching {
            cacheService.loadOldPendingApiCalls(PENDING_API_CALLS_FILE_NAME)
        }

        if (loadPendingApiCallsQueue.isSuccess) {
            cachedApiCallsPerEndpoint = PendingApiCalls()
            loadPendingApiCallsQueue.getOrNull()?.forEach { cachedApiCall ->
                cachedApiCallsPerEndpoint.add(cachedApiCall)
            }
        }

        return cachedApiCallsPerEndpoint
    }

    override fun close() {
    }

    private fun deleteOldestSessions() {
        val sessionsToPurge = cachedSessions.values
            .sortedBy { it.timestampMs }
            .take(cachedSessions.size - MAX_SESSIONS_CACHED + 1)
        if (sessionsToPurge.isNotEmpty()) {
            sessionsToPurge.forEach {
                val message = "Too many cached sessions. Purging session with file name ${it.filename}"
                logger.logWarning(message, SessionPurgeException(message))
                deleteSession(it.sessionId)
            }
        }
    }

    private fun saveSessionBytes(
        sessionId: String,
        sessionStartTimeMs: Long,
        writeSync: Boolean = false,
        snapshot: Boolean = false,
        saveAction: (filename: String) -> Unit
    ) {
        if (writeSync) {
            saveSessionBytesImpl(sessionId, sessionStartTimeMs, saveAction)
        } else {
            // snapshots are low priority compared to state ends + loading/unloading other payload
            // types. State ends are critical as they contain the final information.
            val priority = when {
                snapshot -> TaskPriority.LOW
                else -> TaskPriority.CRITICAL
            }
            backgroundWorker.submit(priority) {
                saveSessionBytesImpl(sessionId, sessionStartTimeMs, saveAction)
            }
        }
    }

    private fun saveSessionBytesImpl(
        sessionId: String,
        sessionStartTimeMs: Long,
        saveAction: (filename: String) -> Unit
    ) {
        try {
            synchronized(cachedSessions) {
                val cachedSession = cachedSessions.getOrElse(sessionId) {
                    CachedSession.create(sessionId, sessionStartTimeMs)
                }
                saveAction(cachedSession.filename)
                if (!cachedSessions.containsKey(cachedSession.sessionId)) {
                    cachedSessions[cachedSession.sessionId] = cachedSession
                }
            }
        } catch (ex: Throwable) {
            logger.logError("Failed to cache current active session", ex)
        }
    }
}
