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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Handles the reading and writing of objects from the app's cache.
 */
internal class EmbraceCacheService(
    private val storageService: StorageService,
    private val serializer: EmbraceSerializer,
    private val logger: InternalEmbraceLogger
) : CacheService {

    /**
     * Holds read-write locks for each filename.
     *
     * If a file is being written then nothing else should read/write it. Otherwise, it's safe
     * to read a file using multiple threads at the same time.
     */
    private val fileLocks = mutableMapOf<String, ReentrantReadWriteLock>()

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        findLock(name).write {
            logger.logDeveloper(TAG, "Attempting to write bytes to $name")
            if (bytes != null) {
                val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
                try {
                    storageService.writeBytesToFile(file, bytes)
                    logger.logDeveloper(TAG, "Bytes cached")
                } catch (ex: Exception) {
                    logger.logWarning("Failed to store cache object " + file.path, ex)
                    deleteFile(name)
                }
            } else {
                logger.logWarning("No bytes to save to file $name")
            }
        }
    }

    override fun loadBytes(name: String): ByteArray? {
        findLock(name).read {
            logger.logDeveloper(TAG, "Attempting to read bytes from $name")
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                return storageService.readBytesFromFile(file)
            } catch (ex: FileNotFoundException) {
                logger.logWarning("Cache file cannot be found " + file.path)
            } catch (ex: Exception) {
                logger.logWarning("Failed to read cache object " + file.path, ex)
            }
            return null
        }
    }

    override fun cachePayload(name: String, action: SerializationAction) {
        findLock(name).write {
            logger.logDeveloper(TAG, "Attempting to write bytes to $name")
            val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
            try {
                file.outputStream().buffered().use(action)
                logger.logDeveloper(TAG, "Bytes cached")
            } catch (ex: Exception) {
                logger.logWarning("Failed to store cache object " + file.path, ex)
                deleteFile(name)
            }
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
        return { stream ->
            logger.logDeveloper(TAG, "Attempting to read bytes from $name")
            findLock(name).read {
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
        findLock(name).write {
            logger.logDeveloper(TAG, "Attempting to cache object: $name")
            val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
            try {
                serializer.toJson(objectToCache, clazz, file.outputStream())
            } catch (ex: Exception) {
                logger.logDebug("Failed to store cache object " + file.path, ex)
                deleteFile(name)
            }
        }
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        findLock(name).read {
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
    }

    override fun deleteFile(name: String): Boolean {
        val success = findLock(name).write {
            logger.logDeveloper(
                "EmbraceCacheService",
                "Attempting to delete file from cache: $name"
            )
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                file.delete()
            } catch (ex: Exception) {
                logger.logDebug("Failed to delete cache object " + file.path)
            }
            false
        }

        // allow lock object to be garbage collected
        fileLocks.remove(name)
        return success
    }

    override fun normalizeCacheAndGetSessionFileIds(): List<String> {
        val sessionFileNames = storageService
            .listFiles { _, name -> name.startsWith(EMBRACE_PREFIX + SESSION_FILE_PREFIX) }
            .map { file -> file.name.substring(EMBRACE_PREFIX.length) }

        // Need to sweep the files directory to clear the temp files left over due to the app process crashing at an inopportune time
        // - Files that contain the latest version of a session (i.e. suffixed of "-new") will be renamed to the proper expected name
        // - Files that were to be deleted in anticipation of a new version but weren't will be delete now (i.e. suffix "-old")
        // - A file named "last_session.json" used by older versions of the SDK that need to be renamed in the new format
        //
        // Since we are dealing with old sessions files, the current process will not modify them outside of this thread.
        // As such, it is safe to do it on this thread.

        val properSessionFileIds = mutableSetOf<String>()
        sessionFileNames.forEach { filename ->
            if (filename.endsWith(OLD_COPY_SUFFIX) || filename.endsWith(TEMP_COPY_SUFFIX)) {
                if (!deleteFile(filename)) {
                    logger.logWarning("Temporary session file for $filename not deleted on startup")
                }
            } else if (filename == OLD_VERSION_FILE_NAME) {
                val previousSdkSession = loadObject(filename, SessionMessage::class.java)
                previousSdkSession?.also { sessionMessage ->
                    runCatching {
                        val session = sessionMessage.session
                        val properSessionFilename = getFileNameForSession(session.sessionId, session.startTime)
                        if (!sessionFileNames.contains(properSessionFilename)) {
                            replaceSessionFile(properSessionFilename, filename)
                            properSessionFileIds.add(properSessionFilename)
                        }
                    }
                }
            } else {
                val isTempFile = filename.endsWith(NEW_COPY_SUFFIX)
                val properFilename = if (isTempFile) filename.removeSuffix(NEW_COPY_SUFFIX) else filename
                if (isTempFile) {
                    if (!replaceSessionFile(filenameToReplace = properFilename, filenameOfReplacement = filename)) {
                        return@forEach
                    }
                }
                properSessionFileIds.add(properFilename)
            }
        }

        return properSessionFileIds.toList()
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        findLock(name).write {
            var isOverwrite = false
            var sessionWriteTempFile: File? = null
            try {
                // First write session to a temp file, then renaming it to the proper session file if it didn't exist before.
                // If it's an overwrite, rename it to denote that serialization was complete, then swap it with the proper session file.
                // That way, if we see a file suffix with "-tmp", we'll know the operation terminated before completing,
                // and thus is likely an incomplete file.
                val sessionFile = storageService.getFileForWrite(EMBRACE_PREFIX + name)
                isOverwrite = sessionFile.exists()
                sessionWriteTempFile = storageService.getFileForWrite(sessionFile.name + TEMP_COPY_SUFFIX)
                // Write new session file to a temporary location
                serializer.toJson(sessionMessage, SessionMessage::class.java, sessionWriteTempFile.outputStream())

                val suffix = if (isOverwrite) {
                    val newSessionFile = storageService.getFileForWrite(sessionFile.name + NEW_COPY_SUFFIX)
                    sessionWriteTempFile.renameTo(newSessionFile)
                    NEW_COPY_SUFFIX
                } else {
                    TEMP_COPY_SUFFIX
                }
                replaceSessionFile(name, name + suffix)
            } catch (ex: Exception) {
                sessionWriteTempFile?.delete()
                val action = if (isOverwrite) {
                    "overwrite"
                } else {
                    "write new"
                }
                logger.logError("Failed to $action session object ", ex)
            }
        }
    }

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        findLock(name).read {
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                val type = Types.newParameterizedType(List::class.java, PendingApiCall::class.java)
                return serializer.fromJson(file.inputStream(), type) as List<PendingApiCall>?
                    ?: emptyList()
            } catch (ex: FileNotFoundException) {
                logger.logDebug("Cache file cannot be found " + file.path)
            } catch (ex: Exception) {
                logger.logDebug("Failed to read cache object " + file.path, ex)
            }
            return null
        }
    }

    override fun transformSession(name: String, transformer: (SessionMessage) -> SessionMessage) {
        findLock(name).write {
            try {
                val sessionMessage = loadObject(name, SessionMessage::class.java) ?: return@write
                val newMessage = transformer(sessionMessage)
                writeSession(name, newMessage)
            } catch (ex: Exception) {
                logger.logError("Failed to transform session object ", ex)
            }
        }
    }

    private fun replaceSessionFile(filenameToReplace: String, filenameOfReplacement: String): Boolean {
        try {
            val sessionFile = storageService.getFileForWrite(EMBRACE_PREFIX + filenameToReplace)
            val oldSessionFile = storageService.getFileForWrite(sessionFile.name + OLD_COPY_SUFFIX)
            if (sessionFile.exists()) {
                sessionFile.renameTo(oldSessionFile)
                oldSessionFile.delete()
            }

            val newSessionFile = storageService.getFileForWrite(EMBRACE_PREFIX + filenameOfReplacement)
            newSessionFile.renameTo(sessionFile)
        } catch (e: Exception) {
            logger.logError("Failed to replace session file ", e)
            return false
        }

        return true
    }

    private fun findLock(name: String) =
        fileLocks.getOrPut(name, ::ReentrantReadWriteLock)

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
        private const val TAG = "EmbraceCacheService"

        const val EMBRACE_PREFIX = "emb_"
        const val OLD_COPY_SUFFIX = "-old"
        const val TEMP_COPY_SUFFIX = "-tmp"
        const val NEW_COPY_SUFFIX = "-new"

        fun getFileNameForSession(sessionId: String, timestampMs: Long): String = "$SESSION_FILE_PREFIX.$timestampMs.$sessionId.json"
    }
}
