package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.threadLocal
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionMessageSerializer
import java.io.File
import java.io.FileNotFoundException
import java.util.regex.Pattern

/**
 * Handles the reading and writing of objects from the app's cache.
 */
internal class EmbraceCacheService(
    fileProvider: Lazy<File>,
    private val serializer: EmbraceSerializer,
    private val logger: InternalEmbraceLogger
) : CacheService {

    private val storageDir: File by fileProvider

    private val sessionMessageSerializer by threadLocal {
        SessionMessageSerializer(serializer)
    }

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        logger.logDeveloper(TAG, "Attempting to write bytes to $name")
        if (bytes != null) {
            val file = File(storageDir, EMBRACE_PREFIX + name)
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
        val file = File(storageDir, EMBRACE_PREFIX + name)
        try {
            return file.readBytes()
        } catch (ex: FileNotFoundException) {
            logger.logWarning("Cache file cannot be found " + file.path)
        } catch (ex: Exception) {
            logger.logWarning("Failed to read cache object " + file.path, ex)
        }
        return null
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
        val file = File(storageDir, EMBRACE_PREFIX + name)
        try {
            serializer.toJson(objectToCache, clazz, file.outputStream())
        } catch (ex: Exception) {
            logger.logDebug("Failed to store cache object " + file.path, ex)
        }
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        val file = File(storageDir, EMBRACE_PREFIX + name)
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
        val file = File(storageDir, EMBRACE_PREFIX + name)
        try {
            return file.delete()
        } catch (ex: Exception) {
            logger.logDebug("Failed to delete cache object " + file.path)
        }
        return false
    }

    override fun deleteObject(name: String): Boolean {
        logger.logDeveloper("EmbraceCacheService", "Attempting to delete: $name")
        val file = File(storageDir, EMBRACE_PREFIX + name)
        try {
            return file.delete()
        } catch (ex: Exception) {
            logger.logDebug("Failed to delete cache object " + file.path)
        }
        return false
    }

    override fun deleteObjectsByRegex(regex: String): Boolean {
        logger.logDeveloper("EmbraceCacheService", "Attempting to delete objects by regex: $regex")
        val pattern = Pattern.compile(regex)
        var result = false
        val filesInCache = storageDir.listFiles()
        if (filesInCache != null) {
            for (cache in filesInCache) {
                if (pattern.matcher(cache.name).find()) {
                    try {
                        result = cache.delete()
                    } catch (ex: Exception) {
                        logger.logDebug("Failed to delete cache object " + cache.path)
                    }
                } else {
                    logger.logDeveloper("EmbraceCacheService", "Objects not found by regex")
                }
            }
        } else {
            logger.logDeveloper("EmbraceCacheService", "There are not files in cache")
        }
        return result
    }

    override fun moveObject(src: String, dst: String): Boolean {
        val srcFile = File(storageDir, EMBRACE_PREFIX + src)
        if (!srcFile.exists()) {
            logger.logDeveloper("EmbraceCacheService", "Source file doesn't exist: $src")
            return false
        }
        val dstFile = File(storageDir, EMBRACE_PREFIX + dst)
        logger.logDeveloper("EmbraceCacheService", "Object moved from $src to $dst")
        return srcFile.renameTo(dstFile)
    }

    override fun listFilenamesByPrefix(prefix: String): List<String>? {
        return storageDir.listFiles { file ->
            file.name.startsWith(EMBRACE_PREFIX + prefix)
        }?.map { file -> file.name.substring(EMBRACE_PREFIX.length) }
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        try {
            logger.logDeveloper(TAG, "Attempting to write bytes to $name")
            val file = File(storageDir, EMBRACE_PREFIX + name)
            file.bufferedWriter().use {
                sessionMessageSerializer.serialize(sessionMessage, it)
            }
            logger.logDeveloper(TAG, "Bytes cached")
        } catch (ex: Throwable) {
            logger.logWarning("Failed to write session with buffered writer", ex)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        val file = File(storageDir, EMBRACE_PREFIX + name)
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

    companion object {
        private const val EMBRACE_PREFIX = "emb_"
        private const val TAG = "EmbraceCacheService"
    }
}
