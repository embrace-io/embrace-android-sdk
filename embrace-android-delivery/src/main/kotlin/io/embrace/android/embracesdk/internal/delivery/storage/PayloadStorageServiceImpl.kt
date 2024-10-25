package io.embrace.android.embracesdk.internal.delivery.storage

import android.content.Context
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ConcurrentSkipListSet
import java.util.zip.GZIPOutputStream

/**
 * Implementation of [PayloadStorageService] that will persist and load payloads as gzipped bytes streams.
 * Callers of the [store] method are expected to pass in a [SerializationAction] that streams back uncompressed bytes,
 * while the callers of [loadPayloadAsStream] should expect the bytes from the stream to be gzipped.
 */
class PayloadStorageServiceImpl(
    outputDir: Lazy<File>,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val processIdProvider: () -> String,
    private val logger: EmbLogger,
    private val storageLimit: Int = 500,
) : PayloadStorageService {

    enum class OutputType(internal val dir: String) {

        /**
         * A complete payload that is ready to send
         */
        PAYLOAD("embrace_payloads"),

        /**
         * An incomplete cached payload that is not ready to send
         */
        CACHE("embrace_cache")
    }

    constructor(
        ctx: Context,
        worker: PriorityWorker<StoredTelemetryMetadata>,
        processIdProvider: () -> String,
        outputType: OutputType,
        logger: EmbLogger,
        storageLimit: Int = 500,
    ) : this(createOutputDir(ctx, outputType, logger), worker, processIdProvider, logger, storageLimit)

    private val payloadDir by outputDir

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

    /**
     * [SerializationAction] is expected to return bytes that are not compressed, and they will be gzipped before
     * being persisted.
     */
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
        GZIPOutputStream(tmpFile.outputStream().buffered()).use { stream ->
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
        worker.submit(metadata) {
            processDelete(metadata)
            callback()
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

    /**
     * The [InputStream] returned is expected to be gzipped, so callers of this method need to unzip it before
     * deserializing the bytes.
     */
    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            metadata.asFile().inputStream().buffered()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
            null
        }
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> = storedFiles.toList()

    override fun getUndeliveredPayloads(): List<StoredTelemetryMetadata> {
        return storedFiles
            .filter { !it.complete && it.processId != processIdProvider() }
            .toList()
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

    private companion object {
        fun createOutputDir(
            ctx: Context,
            outputType: OutputType,
            logger: EmbLogger,
        ) = lazy {
            try {
                File(ctx.filesDir, outputType.dir).apply(File::mkdirs)
            } catch (exc: Throwable) {
                logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
                File(ctx.cacheDir, outputType.dir).apply(File::mkdirs)
            }
        }
    }
}
