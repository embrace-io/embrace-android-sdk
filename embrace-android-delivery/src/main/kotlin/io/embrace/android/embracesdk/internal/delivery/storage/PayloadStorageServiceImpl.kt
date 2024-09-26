package io.embrace.android.embracesdk.internal.delivery.storage

import android.content.Context
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ConcurrentSkipListSet
import java.util.zip.GZIPOutputStream

internal class PayloadStorageServiceImpl(
    private val internalErrorService: InternalErrorService,
    outputDir: Lazy<File>,
    private val storageLimit: Int = 500
) : PayloadStorageService {

    // maintain an in-memory list of payloads to avoid calling listFiles() every time we need
    // to check the storage limit. This will always remain in sync with the actual files on disk
    // as the files are only manipulated from within this class.
    private val storedFiles: ConcurrentSkipListSet<StoredTelemetryMetadata> by lazy {
        val result = runCatching { payloadDir.listFiles() }.getOrNull()
        val files = result?.toList() ?: emptyList()
        val metadata = files.mapNotNull {
            StoredTelemetryMetadata.fromFilename(it.name).getOrNull()
        }
        ConcurrentSkipListSet(storedTelemetryComparator).apply<ConcurrentSkipListSet<StoredTelemetryMetadata>> {
            addAll(metadata)
        }
    }

    private val payloadDir by outputDir

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        try {
            storeImpl(metadata, action)
        } catch (exc: Throwable) {
            internalErrorService.handleInternalError(exc)
        }
    }

    private fun storeImpl(
        metadata: StoredTelemetryMetadata,
        action: SerializationAction
    ) {
        if (pruneStorage(metadata)) {
            return
        }

        // write to a temporary file then rename it, to avoid sending incomplete files
        // to the backend (i.e. where the process terminates or there isn't any disk space).
        val tmpFile = File.createTempFile(metadata.filename, ".tmp")
        GZIPOutputStream(tmpFile.outputStream().buffered()).use { stream ->
            action(stream)
        }

        // move the complete file to its final location.
        metadata.asFile().parentFile?.mkdirs()
        if (tmpFile.renameTo(metadata.asFile())) {
            storedFiles.add(metadata)
        }
    }

    override fun delete(metadata: StoredTelemetryMetadata) {
        try {
            if (metadata.asFile().delete()) {
                storedFiles.remove(metadata)
            }
        } catch (exc: Throwable) {
            if (exc !is FileNotFoundException) {
                internalErrorService.handleInternalError(exc)
            }
        }
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            metadata.asFile().inputStream().buffered()
        } catch (exc: Throwable) {
            internalErrorService.handleInternalError(exc)
            null
        }
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
        val removals = input
            .sortedWith(storedTelemetryComparator)
            .takeLast(removalCount)
        removals.forEach(::delete)
        internalErrorService.handleInternalError(RuntimeException("Pruned payload storage"))

        // notify the caller whether the new payload should be dropped
        val shouldNotPersist = removals.contains(metadata)
        return shouldNotPersist
    }

    private fun StoredTelemetryMetadata.asFile(): File = File(payloadDir, filename)

    companion object {
        private const val OUTPUT_DIR_NAME = "embrace_payloads"

        fun createOutputDir(
            ctx: Context,
            internalErrorService: InternalErrorService
        ): Lazy<File> = lazy {
            try {
                File(ctx.filesDir, OUTPUT_DIR_NAME).apply(File::mkdirs)
            } catch (exc: Throwable) {
                internalErrorService.handleInternalError(exc)
                ctx.cacheDir
            }
        }
    }
}
