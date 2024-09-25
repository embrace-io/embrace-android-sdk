package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPOutputStream

internal class PayloadStorageServiceImpl(
    private val internalErrorService: InternalErrorService,
    outputDir: Lazy<File>,
    private val storageLimit: Int = 500 // TODO: check limit
) : PayloadStorageService {

    private val payloadDir by outputDir

    // TODO: synchronisation

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
        pruneIfNeeded()

        // write to a temporary file then rename it, to avoid sending incomplete files
        // to the backend (i.e. where the process terminates or there isn't any disk space).
        val tmpFile = File.createTempFile(metadata.filename, ".tmp")
        GZIPOutputStream(tmpFile.outputStream().buffered()).use { stream ->
            action(stream)
        }

        // move the complete file to its final location.
        metadata.asFile().parentFile?.mkdirs()
        tmpFile.renameTo(metadata.asFile())
    }

    override fun delete(metadata: StoredTelemetryMetadata) {
        try {
            metadata.asFile().delete()
        } catch (exc: Throwable) {
            internalErrorService.handleInternalError(exc)
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

    private fun pruneIfNeeded() {
        // TODO: future: avoid calling listFiles() every time by retaining an in-memory list
        val files = payloadDir.listFiles() ?: return
        val metadataList = files.mapNotNull {
            StoredTelemetryMetadata.fromFilename(it.name).getOrNull()
        }.sortedWith(storedTelemetryComparator)

        if (metadataList.size >= storageLimit) {
            val excess = metadataList.size - storageLimit + 1
            metadataList.takeLast(excess).forEach { it.asFile().delete() }
        }
    }

    private fun StoredTelemetryMetadata.asFile(): File = File(payloadDir, filename)

    companion object {
        internal const val OUTPUT_DIR_NAME = "embrace_payloads"
    }
}
