package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.traceSection
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.CountingOutputStream
import io.embrace.android.embracesdk.internal.utils.FileWriteCounters
import io.embrace.android.embracesdk.internal.utils.SystemTrace
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
    private val counters: FileWriteCounters = sharedCounters,
) : FileStorageService {

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
        val sharedCounters = FileWriteCounters("sf-bytes-written", "sf-files-written")
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
    ) = SystemTrace.trace(metadata.traceSection("payload-file-write")) {
        if (index.prune(newEntry = metadata)) {
            return@trace
        }

        // write to a temporary file then rename it, to avoid sending incomplete files
        // to the backend (i.e. where the process terminates or there isn't any disk space).
        // create temp file inside the payload dir so any orphans are co-located with payloads and
        // swept on next startup, and name it after the payload so an orphan is overwritten by the
        // next attempt rather than duplicated
        val tmpFile = File(index.rootDir, "${metadata.filename}.tmp")
        try {
            val stream = CountingOutputStream(tmpFile.outputStream().buffered())
            stream.use { action(it) }

            // move the complete file to its final location.
            val dst = index.fileFor(metadata)
            dst.parentFile?.mkdirs()
            if (tmpFile.renameTo(dst)) {
                index.add(metadata)
                counters.recordWrite(stream.written)
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

    override fun payloadSizeBytes(metadata: StoredTelemetryMetadata): Long =
        runCatching { index.fileFor(metadata).length() }.getOrDefault(0L)

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

    override fun pruneSection(entry: StoredTelemetryMetadata?): String =
        entry?.traceSection("storage-index-prune") ?: "storage-index-prune"

    override val removalComparator: Comparator<StoredTelemetryMetadata> =
        compareByDescending(StoredTelemetryMetadata::envelopeType)
            .thenBy(StoredTelemetryMetadata::timestamp)
}
