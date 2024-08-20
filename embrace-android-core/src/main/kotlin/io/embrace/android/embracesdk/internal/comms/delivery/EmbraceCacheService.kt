package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionId
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Handles the reading and writing of objects from the app's cache.
 */
internal class EmbraceCacheService(
    private val storageService: StorageService,
    private val serializer: PlatformSerializer,
    private val logger: EmbLogger
) : CacheService {

    /**
     * Holds read-write locks for each filename.
     *
     * If a file is being written then nothing else should read/write it. Otherwise, it's safe
     * to read a file using multiple threads at the same time.
     */
    private val fileLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    override fun cachePayload(name: String, action: SerializationAction) {
        safeFileWrite(name) { tempFile ->
            tempFile.outputStream().buffered().use(action)
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

    override fun <T> cacheObject(name: String, objectToCache: T, type: Type) {
        safeFileWrite(name) { tempFile ->
            serializer.toJson(objectToCache, type, tempFile.outputStream())
        }
    }

    override fun <T> loadObject(name: String, type: Type): T? {
        findLock(name).read {
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                return serializer.fromJson(file.inputStream(), type)
            } catch (ex: FileNotFoundException) {
                logger.logInfo("Cache file cannot be found " + file.path)
            } catch (ex: Exception) {
                logger.logDebug("Failed to read cache object " + file.path, ex)
            }
            return null
        }
    }

    override fun deleteFile(name: String): Boolean {
        val success = findLock(name).write {
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                file.delete()
            } catch (ex: Exception) {
                logger.logInfo("Failed to delete cache object " + file.path)
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
                val previousSdkSession = loadObject<Envelope<SessionPayload>>(
                    filename,
                    Envelope.sessionEnvelopeType
                )
                previousSdkSession?.also { envelope ->
                    runCatching {
                        val span = envelope.getSessionSpan() ?: return@runCatching
                        val id = envelope.getSessionId() ?: return@runCatching
                        val startTime = span.startTimeNanos?.nanosToMillis() ?: return@runCatching
                        val properSessionFilename = CachedSession.create(id, startTime, false).filename
                        if (!sessionFileNames.contains(properSessionFilename)) {
                            replaceFile(properSessionFilename, filename)
                            properSessionFileIds.add(properSessionFilename)
                        }
                    }
                }
            } else {
                val isTempFile = filename.endsWith(NEW_COPY_SUFFIX)
                val properFilename = if (isTempFile) filename.removeSuffix(NEW_COPY_SUFFIX) else filename
                if (isTempFile) {
                    if (!replaceFile(filenameToReplace = properFilename, filenameOfReplacement = filename)) {
                        return@forEach
                    }
                }
                properSessionFileIds.add(properFilename)
            }
        }

        return properSessionFileIds.toList()
    }

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        findLock(name).read {
            val file = storageService.getFileForRead(EMBRACE_PREFIX + name)
            try {
                val type = TypeUtils.typedList(PendingApiCall::class)
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

    override fun writeSession(name: String, envelope: Envelope<SessionPayload>) {
        cacheObject(name, envelope, Envelope.sessionEnvelopeType)
    }

    override fun transformSession(name: String, transformer: (Envelope<SessionPayload>) -> Envelope<SessionPayload>) {
        findLock(name).write {
            try {
                val envelope = loadObject<Envelope<SessionPayload>>(
                    name,
                    Envelope.sessionEnvelopeType
                ) ?: return@write
                val newMessage = transformer(envelope)
                writeSession(name, newMessage)
            } catch (ex: Exception) {
                logger.logError("Failed to transform session object ", ex)
            }
        }
    }

    /**
     * Write data to a file. If the file already exists, first write it to a temporary location before overwriting the old version,
     * ensuring that data loss would be minimized if the operation is interrupted. Any partially written files will be cleaned up to
     * limit sending of corrupted data to the backend, and log any instances when that happens.
     */
    private fun safeFileWrite(name: String, writeAction: (tempFile: File) -> Unit) {
        findLock(name).write {
            var isOverwrite = false
            var tempFile: File? = null
            try {
                // First write session to a temp file, then renaming it to the proper session file if it didn't exist before.
                // If it's an overwrite, rename it to denote that serialization was complete, then swap it with the proper session file.
                // That way, if we see a file suffix with "-tmp", we'll know the operation terminated before completing,
                // and thus is likely an incomplete file.
                val file = storageService.getFileForWrite(EMBRACE_PREFIX + name)
                isOverwrite = file.exists()
                tempFile = storageService.getFileForWrite(file.name + TEMP_COPY_SUFFIX)

                // Write new file to a temporary location
                writeAction(tempFile)

                val suffix = if (isOverwrite) {
                    val newVersion = storageService.getFileForWrite(file.name + NEW_COPY_SUFFIX)
                    tempFile.renameTo(newVersion)
                    NEW_COPY_SUFFIX
                } else {
                    TEMP_COPY_SUFFIX
                }
                replaceFile(name, name + suffix)
            } catch (ex: Exception) {
                tempFile?.delete()
                val action = if (isOverwrite) {
                    "overwrite"
                } else {
                    "write new"
                }
                logger.logError("Failed to $action session object ", ex)
            }
        }
    }

    private fun replaceFile(filenameToReplace: String, filenameOfReplacement: String): Boolean {
        try {
            val file = storageService.getFileForWrite(EMBRACE_PREFIX + filenameToReplace)
            val oldVersion = storageService.getFileForWrite(file.name + OLD_COPY_SUFFIX)
            if (file.exists()) {
                file.renameTo(oldVersion)
                oldVersion.delete()
            }

            val newVersion = storageService.getFileForWrite(EMBRACE_PREFIX + filenameOfReplacement)
            newVersion.renameTo(file)
        } catch (e: Exception) {
            logger.logError("Failed to replace session file ", e)
            return false
        }

        return true
    }

    private fun findLock(name: String) =
        fileLocks[name] ?: synchronized(fileLocks) {
            fileLocks.getOrPut(name, ::ReentrantReadWriteLock)
        }

    companion object {
        /**
         * File names for all cached sessions start with this prefix
         */
        internal const val SESSION_FILE_PREFIX = "last_session"

        /**
         * Full file name for a session saved with a previous version of the SDK. Note that to
         * preserve backward compatibility, SESSION_FILE_PREFIX must be the start of OLD_VERSION_FILE_NAME
         */
        private const val OLD_VERSION_FILE_NAME = "last_session.json"

        const val EMBRACE_PREFIX: String = "emb_"
        const val OLD_COPY_SUFFIX: String = "-old"
        const val TEMP_COPY_SUFFIX: String = "-tmp"
        const val NEW_COPY_SUFFIX: String = "-new"
    }
}
