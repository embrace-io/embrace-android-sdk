package io.embrace.android.embracesdk.comms.delivery

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.storage.StorageService
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.RuntimeException

/**
 * Handles the reading and writing of objects from the app's cache.
 */
internal class EmbraceCacheService(
    private val storageService: StorageService,
    private val serializer: EmbraceSerializer,
    private val logger: InternalEmbraceLogger
) : CacheService {

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        logger.logDeveloper(TAG, "Attempting to write bytes to $name")
        if (bytes != null) {
            val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
            try {
                if (name == "testfile-truncate") {
                    file.writeHalfTheBytes(bytes)
                } else if (name == "testfile-pause") {
                    file.writeBytesWithPause(bytes)
                }  else {
                    file.writeBytes(bytes)
                }
                logger.logDeveloper(TAG, "Bytes cached")
            } catch (ex: Exception) {
                logger.logWarning("Failed to store cache object " + file.path, ex)
            }
        } else {
            logger.logWarning("No bytes to save to file $name")
        }
    }

    private fun File.writeBytesWithPause(array: ByteArray): Unit = FileOutputStream(this).use {
        val partOne = array.take(7).toByteArray()
        val partTwo = array.takeLast(array.size - 7).toByteArray()
        it.write(partOne)
        Thread.sleep(400)
        it.write(partTwo)
    }

    private fun File.writeHalfTheBytes(array: ByteArray): Unit = FileOutputStream(this).use {
        it.write(array.dropLast(array.size / 2).toByteArray())
        throw RuntimeException("yooooo")
    }

    override fun loadBytes(name: String): ByteArray? {
        logger.logDeveloper(TAG, "Attempting to read bytes from $name")
        val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
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
        val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
        try {
            file.outputStream().buffered().use(action)
            logger.logDeveloper(TAG, "Bytes cached")
        } catch (ex: Exception) {
            runCatching { file.delete() }
            logger.logWarning("Failed to store cache object " + file.path, ex)
        }
    }

    /**
     * Loads a file from the cache and returns a [SerializationAction] that can be used to write
     * the data to a stream.
     * If the file contains compressed data, ConditionalGzipOutputStream won't compress it again.
     * If the file contains uncompressed data, ConditionalGzipOutputStream will compress it.
     * We use ConditionalGzipOutputStream for backwards compatibility, because versions of the SDK
     * before 6.3.0 didn't compress the data.
     */
    override fun loadPayload(name: String): SerializationAction {
        logger.logDeveloper(TAG, "Attempting to read bytes from $name")
        return { stream ->
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                ConditionalGzipOutputStream(stream).use {
                    file.inputStream().buffered().use { input ->
                        input.copyTo(it)
                    }
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
        val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
        try {
            serializer.toJson(objectToCache, clazz, file.outputStream())
        } catch (ex: Exception) {
            logger.logDebug("Failed to store cache object " + file.path, ex)
        }
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
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
        val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
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
            val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
            serializer.toJson(sessionMessage, SessionMessage::class.java, file.outputStream())
            logger.logDeveloper(TAG, "Bytes cached")
        } catch (ex: Throwable) {
            logger.logWarning("Failed to write session with buffered writer", ex)
        }
    }

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
        try {
            val type = Types.newParameterizedType(List::class.java, PendingApiCall::class.java)
            return serializer.fromJson(file.inputStream(), type) as List<PendingApiCall>? ?: emptyList()
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
