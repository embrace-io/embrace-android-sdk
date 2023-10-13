package io.embrace.android.embracesdk.comms.delivery

import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionMessageSerializer
import java.io.Closeable
import java.util.concurrent.ExecutorService

internal class DeliveryCacheManager(
    private val cacheService: CacheService,
    private val executorService: ExecutorService,
    private val logger: InternalEmbraceLogger,
    private val clock: Clock,
    private val serializer: EmbraceSerializer
) : Closeable {

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
         * File names for failed api calls
         */
        private const val FAILED_API_CALLS_FILE_NAME = "failed_api_calls.json"

        @VisibleForTesting
        const val MAX_SESSIONS_CACHED = 64

        private const val TAG = "DeliveryCacheManager"
    }

    private val sessionMessageSerializer by lazy {
        SessionMessageSerializer(serializer)
    }

    // The session id is used as key for this map
    // This list is initialized when getAllCachedSessions() is called.
    private val cachedSessions = mutableMapOf<String, CachedSession>()

    fun saveSession(sessionMessage: SessionMessage): ByteArray? {
        if (cachedSessions.size >= MAX_SESSIONS_CACHED) {
            deleteOldestSessions()
        }
        val sessionBytes: ByteArray = sessionMessageSerializer.serialize(sessionMessage).toByteArray()
        saveBytes(sessionMessage.session.sessionId, sessionBytes)
        return sessionBytes
    }

    fun loadSession(sessionId: String): SessionMessage? {
        cachedSessions[sessionId]?.let { cachedSession ->
            return loadSession(cachedSession)
        }
        logger.logError("Session $sessionId is not in cache")
        return null
    }

    fun loadSessionBytes(sessionId: String): ByteArray? {
        cachedSessions[sessionId]?.let { cachedSession ->
            return executorService.submit<ByteArray?> { loadPayload(cachedSession.filename) }.get()
        }
        logger.logError("Session $sessionId is not in cache")
        return null
    }

    fun deleteSession(sessionId: String) {
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

    fun getAllCachedSessionIds(): List<String> {
        val allSessions = cacheService.listFilenamesByPrefix(SESSION_FILE_PREFIX)
        allSessions?.forEach { filename ->
            if (filename == OLD_VERSION_FILE_NAME) {
                // If a cached session from a previous version of the SDK is found,
                // load and save it again using the new naming schema
                val previousSdkSession =
                    cacheService.loadObject(filename, SessionMessage::class.java)
                previousSdkSession?.also {
                    // When saved, the new session filename is also added to cachedSessions
                    saveSession(it)
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

    fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage): ByteArray? {
        val baId = backgroundActivityMessage.backgroundActivity.sessionId
        val baBytes = serializer.bytesFromPayload(
            backgroundActivityMessage,
            BackgroundActivityMessage::class.java
        )
        // Do not add background activities to disk if we are over the limit
        if (cachedSessions.size < MAX_SESSIONS_CACHED || cachedSessions.containsKey(baId)) {
            baBytes?.let { saveBytes(baId, it) }
        }
        return baBytes
    }

    fun loadBackgroundActivity(backgroundActivityId: String): ByteArray? {
        cachedSessions[backgroundActivityId]?.let { cachedSession ->
            return executorService.submit<ByteArray?> { loadPayload(cachedSession.filename) }.get()
        }
        logger.logWarning("Background activity $backgroundActivityId is not in cache")
        return null
    }

    fun saveCrash(crash: EventMessage) {
        cacheService.cacheObject(CRASH_FILE_NAME, crash, EventMessage::class.java)
    }

    fun loadCrash(): EventMessage? {
        return cacheService.loadObject(CRASH_FILE_NAME, EventMessage::class.java)
    }

    fun deleteCrash() {
        cacheService.deleteFile(CRASH_FILE_NAME)
    }

    fun savePayload(bytes: ByteArray): String {
        val name = "payload_" + Uuid.getEmbUuid()
        executorService.submit {
            cacheService.cacheBytes(name, bytes)
        }
        return name
    }

    fun loadPayload(name: String): ByteArray? {
        return cacheService.loadBytes(name)
    }

    fun deletePayload(name: String) {
        executorService.submit {
            cacheService.deleteFile(name)
        }
    }

    fun saveFailedApiCalls(failedApiCalls: DeliveryFailedApiCalls) {
        logger.logDeveloper(TAG, "Saving failed api calls")
        serializer.bytesFromPayload(
            failedApiCalls,
            DeliveryFailedApiCalls::class.java
        )?.let {
            executorService.submit {
                cacheService.cacheBytes(FAILED_API_CALLS_FILE_NAME, it)
            }
        }
    }

    fun loadFailedApiCalls(): DeliveryFailedApiCalls {
        logger.logDeveloper(TAG, "Loading failed api calls")
        val cached = executorService.submit<DeliveryFailedApiCalls> {
            cacheService.loadObject(FAILED_API_CALLS_FILE_NAME, DeliveryFailedApiCalls::class.java)
        }.get()
        return if (cached != null) {
            cached
        } else {
            logger.logDeveloper(TAG, "No failed api calls cache found")
            DeliveryFailedApiCalls()
        }
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

    private fun saveBytes(sessionId: String, bytes: ByteArray) {
        executorService.submit {
            try {
                val cachedSession = cachedSessions.getOrElse(sessionId) {
                    CachedSession(
                        sessionId,
                        clock.now()
                    )
                }
                cacheService.cacheBytes(cachedSession.filename, bytes)
                if (!cachedSessions.containsKey(cachedSession.sessionId)) {
                    cachedSessions[cachedSession.sessionId] = cachedSession
                }
                logger.logDeveloper(TAG, "Session message successfully cached.")
            } catch (ex: Exception) {
                logger.logError("Failed to cache current active session", ex)
            }
        }
    }

    @VisibleForTesting
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
