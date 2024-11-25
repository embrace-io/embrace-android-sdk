package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.RejectedExecutionException

internal class FileStorageServiceImpl(
    outputDir: Lazy<File>,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val logger: EmbLogger,
    private val storageLimit: Int = 500,
) : FileStorageService {

    private val payloadDir by outputDir

    // maintain an in-memory list of payloads to avoid calling listFiles() every time we need
    // to check the storage limit. This will always remain in sync with the actual files on disk
    // as the files are only manipulated from within this class.
    private val storedFiles: CopyOnWriteArraySet<StoredTelemetryMetadata> by lazy {
        val result = runCatching { payloadDir.listFiles() }.getOrNull()
        val files = result?.toList() ?: emptyList()
        val metadata = files.mapNotNull {
            StoredTelemetryMetadata.fromFilename(it.name).getOrNull()
        }
        CopyOnWriteArraySet(metadata)
    }

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        try {
            storeImpl(metadata, action)
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
        }
    }

    private fun storeImpl(
        metadata: StoredTelemetryMetadata,
        action: SerializationAction,
    ) {
        if (pruneStorage(metadata)) {
            return
        }

        // write to a temporary file then rename it, to avoid sending incomplete files
        // to the backend (i.e. where the process terminates or there isn't any disk space).
        val tmpFile = File.createTempFile(metadata.filename, ".tmp")
        tmpFile.outputStream().buffered().use { stream ->
            action(stream)
        }

        // move the complete file to its final location.
        val dst = metadata.asFile()
        dst.parentFile?.mkdirs()
        if (tmpFile.renameTo(dst)) {
            storedFiles.add(metadata)
        }
    }

    override fun delete(metadata: StoredTelemetryMetadata, callback: () -> Unit) {
        val action = {
            processDelete(metadata)
            callback()
        }
        try {
            worker.submit(metadata, action)
        } catch (exc: RejectedExecutionException) { // handle JVM crash case where worker is shutdown
            action()
        }
    }

    private fun processDelete(metadata: StoredTelemetryMetadata) {
        try {
            if (metadata.asFile().delete()) {
                storedFiles.remove(metadata)
            }
        } catch (exc: Throwable) {
            if (exc !is FileNotFoundException) {
                logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
            }
        }
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            metadata.asFile().inputStream().buffered()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
            null
        }
    }

    override fun getStoredPayloads(): List<StoredTelemetryMetadata> {
        return storedFiles.toList()
    }

    private fun pruneStorage(metadata: StoredTelemetryMetadata): Boolean {
        val count = storedFiles.size
        if (count < storageLimit) {
            return false
        }
        val input = storedFiles.plus(metadata)
        val removalCount = input.size - storageLimit
        if (removalCount < 0) {
            return false
        }
        val removals = input.sortedWith(
            compareByDescending(StoredTelemetryMetadata::envelopeType)
                .thenBy(StoredTelemetryMetadata::timestamp)
        )
            .take(removalCount)
        removals.forEach(::processDelete)
        logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, RuntimeException("Pruned payload storage"))

        // notify the caller whether the new payload should be dropped
        val shouldNotPersist = removals.contains(metadata)
        return shouldNotPersist
    }

    private fun StoredTelemetryMetadata.asFile(): File = File(payloadDir, filename)
}
