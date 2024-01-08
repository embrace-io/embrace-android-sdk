package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.storage.StorageService
import java.io.File
import java.io.FileNotFoundException

/**
 * Handles the reading and writing of objects from the app's cache.
 * Previous versions of the SDK used the cache directory for cached files.
 * Since v6.3.0, the files directory is used instead.
 */
internal class EmbraceCacheService(
    private val storageService: StorageService,
    private val serializer: EmbraceSerializer,
    private val logger: InternalEmbraceLogger
) : CacheService {

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        logger.logDeveloper(TAG, "Attempting to write bytes to $name")
        if (bytes != null) {
            val file = getFileFromFilesDir(name)
            try {
                file.writeBytes(bytes)
                logger.logDeveloper(TAG, "Bytes cached")
            } catch (ex: Exception) {
                logger.logWarning("Failed to store cache object " + file.path, ex)
            }
        } else {
            logger.logWarning("No bytes to save to file $name")
        }
    }

    override fun loadBytes(name: String): ByteArray? {
        logger.logDeveloper(TAG, "Attempting to read bytes from $name")
        val file = getFileFromFilesOrCacheDir(name)
        try {
            return file.readBytes()
        } catch (ex: FileNotFoundException) {
            logger.logWarning("Cache file cannot be found " + file.path)
        } catch (ex: Exception) {
            logger.logWarning("Failed to read cache object " + file.path, ex)
        }
        return null
    }

    override fun cachePayload(name: String, action: SerializationAction) {
        logger.logDeveloper(TAG, "Attempting to write bytes to $name")
        val file = getFileFromFilesDir(name)
        try {
            file.outputStream().buffered().use(action)
            logger.logDeveloper(TAG, "Bytes cached")
        } catch (ex: Exception) {
            runCatching { file.delete() }
            logger.logWarning("Failed to store cache object " + file.path, ex)
        }
    }

    override fun loadPayload(name: String): SerializationAction {
        logger.logDeveloper(TAG, "Attempting to read bytes from $name")
        return { stream ->
            val file = getFileFromFilesOrCacheDir(name)
            try {
                file.inputStream().buffered().use { input ->
                    input.copyTo(stream)
                }
            } catch (ex: FileNotFoundException) {
                logger.logWarning("Cache file cannot be found " + file.path)
            } catch (ex: Exception) {
                logger.logWarning("Failed to read cache object " + file.path, ex)
            }
        }
    }

    /**
     * Writes a file to the cache. Must be serializable JSON.
     *
     *
     * If writing the object to the cache fails, an exception is logged.
     *
     * @param name   the name of the object to write
     * @param objectToCache the object to write
     * @param clazz  the class of the object to write
     * @param <T>    the type of the object to write
     */
    override fun <T> cacheObject(name: String, objectToCache: T, clazz: Class<T>) {
        logger.logDeveloper(TAG, "Attempting to cache object: $name")
        val file = getFileFromFilesDir(name)
        try {
            serializer.toJson(objectToCache, clazz, file.outputStream())
        } catch (ex: Exception) {
            logger.logDebug("Failed to store cache object " + file.path, ex)
        }
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        val file = getFileFromFilesOrCacheDir(name)
        try {
            return serializer.fromJson(file.inputStream(), clazz)
        } catch (ex: FileNotFoundException) {
            logger.logDebug("Cache file cannot be found " + file.path)
        } catch (ex: Exception) {
            logger.logDebug("Failed to read cache object " + file.path, ex)
        }
        return null
    }

    override fun deleteFile(name: String): Boolean {
        logger.logDeveloper("EmbraceCacheService", "Attempting to delete file from cache: $name")
        val file = getFileFromFilesOrCacheDir(name)
        try {
            return file.delete()
        } catch (ex: Exception) {
            logger.logDebug("Failed to delete cache object " + file.path)
        }
        return false
    }

    override fun listFilenamesByPrefix(prefix: String): List<String> {
        return storageService.listFiles { _, name ->
            name.startsWith(EMBRACE_PREFIX + prefix)
        }.map { file -> file.name.substring(EMBRACE_PREFIX.length) }
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        try {
            logger.logDeveloper(TAG, "Attempting to write bytes to $name")
            val file = getFileFromFilesDir(name)
            serializer.toJson(sessionMessage, SessionMessage::class.java, file.outputStream())
            logger.logDeveloper(TAG, "Bytes cached")
        } catch (ex: Throwable) {
            logger.logWarning("Failed to write session with buffered writer", ex)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        val file = getFileFromFilesOrCacheDir(name)
        try {
            val results = serializer.fromJson(file.inputStream(), ArrayList::class.java)
            return results as List<PendingApiCall>? ?: return emptyList()
        } catch (ex: FileNotFoundException) {
            logger.logDebug("Cache file cannot be found " + file.path)
        } catch (ex: Exception) {
            logger.logDebug("Failed to read cache object " + file.path, ex)
        }
        return null
    }

    /**
     * Gets a file from the files directory.
     * Generally used to write files, so we store them in the files directory.
     * The files directory is the default directory.
     */
    private fun getFileFromFilesDir(name: String): File {
        return storageService.getFile(EMBRACE_PREFIX + name, false)
    }

    /**
     * Gets a file from the files directory or the cache directory if it doesn't exist.
     * Generally used to read files; we check for existing files
     * in the cache directory for backwards compatibility.
     */
    private fun getFileFromFilesOrCacheDir(name: String): File {
        return storageService.getFile(EMBRACE_PREFIX + name, true)
    }

    companion object {
        private const val EMBRACE_PREFIX = "emb_"
        private const val TAG = "EmbraceCacheService"
    }
}
