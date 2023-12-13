package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType
import java.io.Closeable
import java.util.concurrent.ExecutorService

internal class EmbraceDeliveryCacheManager(
    private val cacheService: CacheService,
    private val executorService: ExecutorService,
    private val logger: InternalEmbraceLogger,
    private val clock: Clock,
    private val serializer: EmbraceSerializer
) : Closeable, DeliveryCacheManager {

    companion object {
        /**
         * File names for all cached sessions start with this prefix
         */
        private const val SESSION_FILE_PREFIX = "last_session"

        /**
         * Full file name for a session saved with a previous version of the SDK. Note that to
         * preserve backward compatibility, SESSION_FILE_PREFIX must be the start of OLD_VERSION_FILE_NAME
         */
        private const val OLD_VERSION_FILE_NAME = "last_session.json"

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
    ): ByteArray? {
        return when (snapshotType) {
            SessionSnapshotType.PERIODIC_CACHE -> {
                saveSessionImpl(sessionMessage, false) { filename ->
                    cacheService.writeSession(filename, sessionMessage)
                }
                null
            }
            else -> {
                val sessionBytes: ByteArray = serializer.toJson(sessionMessage).toByteArray()
                saveSessionImpl(sessionMessage, snapshotType == SessionSnapshotType.JVM_CRASH) { filename ->
                    cacheService.cacheBytes(filename, sessionBytes)
                }
                sessionBytes
            }
        }
    }

    private fun saveSessionImpl(
        sessionMessage: SessionMessage,
        writeSync: Boolean,
        saveAction: (filename: String) -> Unit
    ) {
        try {
            if (cachedSessions.size >= MAX_SESSIONS_CACHED) {
                deleteOldestSessions()
            }
            saveBytes(sessionMessage.session.sessionId, writeSync, saveAction)
        } catch (exc: Throwable) {
            logger.logError("Save session failed", exc, true)
            throw exc
        }
    }

    override fun loadSession(sessionId: String): SessionMessage? {
        cachedSessions[sessionId]?.let { cachedSession ->
            return loadSession(cachedSession)
        }
        logger.logError("Session $sessionId is not in cache")
        return null
    }

    override fun loadSessionBytes(sessionId: String): ByteArray? {
        cachedSessions[sessionId]?.let { cachedSession ->
            return executorService.submit<ByteArray?> { loadPayload(cachedSession.filename) }.get()
        }
        logger.logError("Session $sessionId is not in cache")
        return null
    }

    override fun deleteSession(sessionId: String) {
        cachedSessions[sessionId]?.let { cachedSession ->
            executorService.submit {
                try {
                    cacheService.deleteFile(cachedSession.filename)
                    cachedSessions.remove(sessionId)
                } catch (ex: Exception) {
                    logger.logError("Could not remove session from cache: $sessionId")
                }
            }
        }
    }

    override fun getAllCachedSessionIds(): List<String> {
        val allSessions = cacheService.listFilenamesByPrefix(SESSION_FILE_PREFIX)
        allSessions?.forEach { filename ->
            if (filename == OLD_VERSION_FILE_NAME) {
                // If a cached session from a previous version of the SDK is found,
                // load and save it again using the new naming schema
                val previousSdkSession =
                    cacheService.loadObject(filename, SessionMessage::class.java)
                previousSdkSession?.also {
                    // When saved, the new session filename is also added to cachedSessions
                    saveSession(it, SessionSnapshotType.NORMAL_END)
                    executorService.submit {
                        cacheService.deleteFile(filename)
                    }
                }
            }
            val values = filename.split('.')
            if (values.size != 4) {
                logger.logError("Unrecognized cached file: $filename")
                return@forEach
            }
            val timestamp = values[1].toLongOrNull()
            timestamp?.also {
                val sessionId = values[2]
                cachedSessions[sessionId] = CachedSession(sessionId, it)
            } ?: run {
                logger.logError("Could not parse timestamp ${values[2]}")
            }
        }
        return cachedSessions.keys.toList()
    }

    override fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage): ByteArray {
        val baId = backgroundActivityMessage.backgroundActivity.sessionId
        val baBytes = serializer.toJson(backgroundActivityMessage).toByteArray()
        // Do not add background activities to disk if we are over the limit
        if (cachedSessions.size < MAX_SESSIONS_CACHED || cachedSessions.containsKey(baId)) {
            saveBytes(baId) { filename ->
                cacheService.cacheBytes(filename, baBytes)
            }
        }
        return baBytes
    }

    override fun loadBackgroundActivity(backgroundActivityId: String): ByteArray? {
        cachedSessions[backgroundActivityId]?.let { cachedSession ->
            return executorService.submit<ByteArray?> { loadPayload(cachedSession.filename) }.get()
        }
        logger.logWarning("Background activity $backgroundActivityId is not in cache")
        return null
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

    override fun savePayload(bytes: ByteArray): String {
        val name = "payload_" + Uuid.getEmbUuid()
        executorService.submit {
            cacheService.cacheBytes(name, bytes)
        }
        return name
    }

    override fun loadPayload(name: String): ByteArray? {
        return cacheService.loadBytes(name)
    }

    override fun deletePayload(name: String) {
        executorService.submit {
            cacheService.deleteFile(name)
        }
    }

    /**
     * Saves the [PendingApiCalls] map to a file named [PENDING_API_CALLS_FILE_NAME].
     */
    override fun savePendingApiCalls(pendingApiCalls: PendingApiCalls) {
        logger.logDeveloper(TAG, "Saving pending api calls")
        val bytes = serializer.toJson(pendingApiCalls).toByteArray()
        executorService.submit {
            cacheService.cacheBytes(PENDING_API_CALLS_FILE_NAME, bytes)
        }
    }

    /**
     * Loads the [PendingApiCalls] map from a file named [PENDING_API_CALLS_FILE_NAME].
     * If loadObject returns null, it tries to load the old version of the file which was storing
     * [PendingApiCallsQueue] instead of [PendingApiCalls].
     */
    override fun loadPendingApiCalls(): PendingApiCalls {
        logger.logDeveloper(TAG, "Loading pending api calls")
        val cached = executorService.submit<PendingApiCalls> {
            val loadApiCallsResult = runCatching {
                cacheService.loadObject(PENDING_API_CALLS_FILE_NAME, PendingApiCalls::class.java)
            }
            loadApiCallsResult.getOrNull() ?: loadPendingApiCallsOldVersion()
        }.get()
        return if (cached != null) {
            cached
        } else {
            logger.logDeveloper(TAG, "No pending api calls cache found")
            PendingApiCalls()
        }
    }

    /**
     * Loads the old version of the [PENDING_API_CALLS_FILE_NAME] file where
     * it was storing [PendingApiCallsQueue] instead of [PendingApiCalls]
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

    private fun loadSession(cachedSession: CachedSession): SessionMessage? {
        return executorService.submit<SessionMessage?> {
            try {
                val sessionMessage = cacheService.loadObject(
                    cachedSession.filename,
                    SessionMessage::class.java
                )
                if (sessionMessage != null) {
                    logger.logDeveloper(TAG, "Successfully fetched previous session message.")
                    return@submit sessionMessage
                }
            } catch (ex: Exception) {
                logger.logError("Failed to load previous cached session message", ex)
            }
            null
        }.get()
    }

    private fun deleteOldestSessions() {
        cachedSessions.values
            .sortedBy { it.timestamp }
            .take(cachedSessions.size - MAX_SESSIONS_CACHED + 1)
            .forEach { deleteSession(it.sessionId) }
    }

    private fun saveBytes(
        sessionId: String,
        writeSync: Boolean = false,
        saveAction: (filename: String) -> Unit
    ) {
        if (writeSync) {
            saveBytesImpl(sessionId, saveAction)
        } else {
            executorService.submit {
                saveBytesImpl(sessionId, saveAction)
            }
        }
    }

    private fun saveBytesImpl(
        sessionId: String,
        saveAction: (filename: String) -> Unit
    ) {
        try {
            val cachedSession = cachedSessions.getOrElse(sessionId) {
                CachedSession(
                    sessionId,
                    clock.now()
                )
            }
            saveAction(cachedSession.filename)
            if (!cachedSessions.containsKey(cachedSession.sessionId)) {
                cachedSessions[cachedSession.sessionId] = cachedSession
            }
            logger.logDeveloper(TAG, "Session message successfully cached.")
        } catch (ex: Throwable) {
            logger.logError("Failed to cache current active session", ex, true)
        }
    }

    data class CachedSession(
        val filename: String,
        val sessionId: String,
        val timestamp: Long?
    ) {
        constructor(
            sessionId: String,
            timestamp: Long
        ) : this(
            "$SESSION_FILE_PREFIX.$timestamp.$sessionId.json",
            sessionId,
            timestamp
        )
    }
}
