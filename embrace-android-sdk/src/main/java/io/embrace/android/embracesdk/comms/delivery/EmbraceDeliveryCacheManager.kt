package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager.Companion.PENDING_API_CALLS_FILE_NAME
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.isV2Payload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.io.Closeable

internal class EmbraceDeliveryCacheManager(
    private val cacheService: CacheService,
    private val backgroundWorker: BackgroundWorker,
    private val logger: InternalEmbraceLogger,
    private val clock: Clock
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

        private const val TAG = "DeliveryCacheManager"
    }

    // The session id is used as key for this map
    // This list is initialized when getAllCachedSessions() is called.
    private val cachedSessions = mutableMapOf<String, CachedSession>()

    override fun saveSession(
        sessionMessage: SessionMessage,
        snapshotType: SessionSnapshotType
    ) {
        try {
            if (cachedSessions.size >= MAX_SESSIONS_CACHED) {
                deleteOldestSessions()
            }
            val sessionId = sessionMessage.session.sessionId
            val writeSync = snapshotType == SessionSnapshotType.JVM_CRASH
            val snapshot = snapshotType == SessionSnapshotType.PERIODIC_CACHE

            saveSessionBytes(sessionId, writeSync, snapshot, sessionMessage.isV2Payload()) { filename: String ->
                Systrace.traceSynchronous("serialize-session") {
                    cacheService.writeSession(filename, sessionMessage)
                }
            }
        } catch (exc: Throwable) {
            logger.logError("Save session failed", exc, true)
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
            }
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
    override fun savePendingApiCalls(pendingApiCalls: PendingApiCalls) {
        logger.logDeveloper(TAG, "Saving pending api calls")
        backgroundWorker.submit {
            cacheService.cacheObject(PENDING_API_CALLS_FILE_NAME, pendingApiCalls, PendingApiCalls::class.java)
        }
    }

    /**
     * Loads the [PendingApiCalls] map from a file named [PENDING_API_CALLS_FILE_NAME].
     * If loadObject returns null, it tries to load the old version of the file which was storing
     * a list of [PendingApiCall] instead of [PendingApiCalls].
     */
    override fun loadPendingApiCalls(): PendingApiCalls =
        runCatching { cacheService.loadObject(PENDING_API_CALLS_FILE_NAME, PendingApiCalls::class.java) }.getOrNull()
            ?: loadPendingApiCallsOldVersion()
            ?: PendingApiCalls()

    /**
     * The caller of this method needs to be run in the [WorkerName.DELIVERY_CACHE] thread so all session writes are done serially
     */
    override fun transformSession(sessionId: String, transformer: (SessionMessage) -> SessionMessage) {
        val filename = cachedSessions[sessionId]?.filename ?: return
        cacheService.transformSession(filename, transformer)
    }

    /**
     * Loads the old version of the [PENDING_API_CALLS_FILE_NAME] file where
     * it was storing a list of [PendingApiCall] instead of [PendingApiCalls]
     */
    private fun loadPendingApiCallsOldVersion(): PendingApiCalls? {
        logger.logDeveloper(TAG, "Loading old version of pending api calls")
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
        writeSync: Boolean = false,
        snapshot: Boolean = false,
        v2Payload: Boolean,
        saveAction: (filename: String) -> Unit
    ) {
        if (writeSync) {
            saveSessionBytesImpl(sessionId, v2Payload, saveAction)
        } else {
            // snapshots are low priority compared to state ends + loading/unloading other payload
            // types. State ends are critical as they contain the final information.
            val priority = when {
                snapshot -> TaskPriority.LOW
                else -> TaskPriority.CRITICAL
            }
            backgroundWorker.submit(priority) {
                saveSessionBytesImpl(sessionId, v2Payload, saveAction)
            }
        }
    }

    private fun saveSessionBytesImpl(
        sessionId: String,
        v2Payload: Boolean,
        saveAction: (filename: String) -> Unit
    ) {
        try {
            synchronized(cachedSessions) {
                val cachedSession = cachedSessions.getOrElse(sessionId) {
                    CachedSession.create(sessionId, clock.now(), v2Payload)
                }
                saveAction(cachedSession.filename)
                if (!cachedSessions.containsKey(cachedSession.sessionId)) {
                    cachedSessions[cachedSession.sessionId] = cachedSession
                }
                logger.logDeveloper(TAG, "Session message successfully cached.")
            }
        } catch (ex: Throwable) {
            logger.logError("Failed to cache current active session", ex, true)
        }
    }
}

internal class SessionPurgeException(message: String) : Exception(message)
