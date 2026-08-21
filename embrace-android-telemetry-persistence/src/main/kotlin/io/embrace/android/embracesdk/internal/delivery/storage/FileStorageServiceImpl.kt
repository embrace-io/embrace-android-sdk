package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.RejectedExecutionException

class FileStorageServiceImpl(
    outputDir: Lazy<File>,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val logger: InternalLogger,
    clock: Clock,
    storageLimit: Int = 500,
    maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) : FileStorageService {

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
    }

    private val index = StoredEntryIndex(
        outputDir = outputDir,
        layout = StoredTelemetryMetadataLayout,
        clock = clock,
        logger = logger,
        errorType = InternalErrorType.PayloadStorageFail,
        storageLimit = storageLimit,
        maxAgeMs = maxAgeMs,
    )

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        try {
            storeImpl(metadata, action)
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PayloadStorageFail, exc)
        }
    }

    private fun storeImpl(
        metadata: StoredTelemetryMetadata,
        action: SerializationAction,
    ) {
        if (index.prune(newEntry = metadata)) {
            return
        }

        // write to a temporary file then rename it, to avoid sending incomplete files
        // to the backend (i.e. where the process terminates or there isn't any disk space).
        // create temp file inside the payload dir so any orphans
        // are co-located with payloads and swept on next startup
        val tmpFile = File.createTempFile(metadata.filename, ".tmp", index.rootDir)
        try {
            tmpFile.outputStream().buffered().use { stream ->
                action(stream)
            }

            // move the complete file to its final location.
            val dst = index.fileFor(metadata)
            dst.parentFile?.mkdirs()
            if (tmpFile.renameTo(dst)) {
                index.add(metadata)
            }
        } finally {
            // clean up the temp file on any failure
            tmpFile.delete()
        }
    }

    override fun delete(metadata: StoredTelemetryMetadata, callback: () -> Unit) {
        val action = {
            index.delete(metadata)
            callback()
        }
        try {
            worker.submit(metadata, action)
        } catch (exc: RejectedExecutionException) { // handle JVM crash case where worker is shutdown
            action()
        }
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            index.fileFor(metadata).inputStream().buffered()
        } catch (_: FileNotFoundException) {
            null
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PayloadStorageFail, exc)
            null
        }
    }

    override fun getStoredPayloads(): List<StoredTelemetryMetadata> = index.storedEntries()
}

/**
 * Layout for telemetry payloads, which occupy one file per payload with the metadata encoded in the
 * filename.
 */
internal object StoredTelemetryMetadataLayout : StoredEntryLayout<StoredTelemetryMetadata> {

    override fun fromName(name: String): StoredTelemetryMetadata? =
        StoredTelemetryMetadata.fromFilename(name).getOrNull()

    override fun fileFor(rootDir: File, entry: StoredTelemetryMetadata): File =
        File(rootDir, entry.filename)

    override fun delete(file: File) {
        file.delete()
    }

    override fun timestampOf(entry: StoredTelemetryMetadata): Long = entry.timestamp

    override val removalComparator: Comparator<StoredTelemetryMetadata> =
        compareByDescending(StoredTelemetryMetadata::envelopeType)
            .thenBy(StoredTelemetryMetadata::timestamp)
}
