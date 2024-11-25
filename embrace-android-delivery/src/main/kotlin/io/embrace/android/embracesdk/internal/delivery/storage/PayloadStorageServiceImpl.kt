package io.embrace.android.embracesdk.internal.delivery.storage

import android.content.Context
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPOutputStream

/**
 * Implementation of [PayloadStorageService] that will persist and load payloads as gzipped bytes streams.
 * Callers of the [store] method are expected to pass in a [SerializationAction] that streams back uncompressed bytes,
 * while the callers of [loadPayloadAsStream] should expect the bytes from the stream to be gzipped.
 */
class PayloadStorageServiceImpl(
    outputDir: Lazy<File>,
    worker: PriorityWorker<StoredTelemetryMetadata>,
    private val processIdProvider: () -> String,
    logger: EmbLogger,
    private val deliveryTracer: DeliveryTracer? = null,
    storageLimit: Int = 500,
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
        deliveryTracer: DeliveryTracer? = null,
        storageLimit: Int = 500,
    ) : this(createOutputDir(ctx, outputType, logger), worker, processIdProvider, logger, deliveryTracer, storageLimit)

    private val fileStorageService: FileStorageService = FileStorageServiceImpl(
        outputDir,
        worker,
        logger,
        storageLimit
    )

    /**
     * [SerializationAction] is expected to return bytes that are not compressed, and they will be gzipped before
     * being persisted.
     */
    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        fileStorageService.store(metadata) { stream ->
            GZIPOutputStream(stream).use(action)
        }
        deliveryTracer?.onStore(metadata)
    }

    override fun delete(metadata: StoredTelemetryMetadata, callback: () -> Unit) {
        fileStorageService.delete(metadata, callback)
        deliveryTracer?.onDelete(metadata)
    }

    /**
     * The [InputStream] returned is expected to be gzipped, so callers of this method need to unzip it before
     * deserializing the bytes.
     */
    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return fileStorageService.loadPayloadAsStream(metadata).apply {
            deliveryTracer?.onLoadPayloadAsStream(this != null)
        }
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> {
        return fileStorageService.getStoredPayloads().sortedWith(storedTelemetryComparator).apply {
            deliveryTracer?.onGetPayloadsByPriority(this)
        }
    }

    override fun getUndeliveredPayloads(): List<StoredTelemetryMetadata> {
        return fileStorageService.getStoredPayloads().sortedWith(storedTelemetryComparator)
            .filter { !it.complete && it.processId != processIdProvider() }
            .toList().apply {
                deliveryTracer?.onGetUndeliveredPayloads(this)
            }
    }

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
