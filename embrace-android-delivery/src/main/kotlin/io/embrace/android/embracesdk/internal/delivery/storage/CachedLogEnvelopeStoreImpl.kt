package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File

class CachedLogEnvelopeStoreImpl(
    outputDir: Lazy<File>,
    worker: PriorityWorker<StoredTelemetryMetadata>,
    logger: EmbLogger,
    private val serializer: PlatformSerializer,
    storageLimit: Int = 100,
) : CachedLogEnvelopeStore {

    private val fileStorageService: FileStorageService = FileStorageServiceImpl(
        outputDir,
        worker,
        logger,
        storageLimit
    )

    override fun create(
        storedTelemetryMetadata: StoredTelemetryMetadata,
        resource: EnvelopeResource,
        metadata: EnvelopeMetadata,
    ) {
        fileStorageService.store(storedTelemetryMetadata) { stream ->
            serializer.toJson(
                LogPayload().createLogEnvelope(
                    resource,
                    metadata
                ),
                checkNotNull(storedTelemetryMetadata.envelopeType.serializedType),
                stream
            )
        }
    }

    override fun get(storedTelemetryMetadata: StoredTelemetryMetadata): Envelope<LogPayload>? =
        runCatching {
            fileStorageService.loadPayloadAsStream(storedTelemetryMetadata)?.let { inputStream ->
                serializer.fromJson<Envelope<LogPayload>>(
                    inputStream = inputStream,
                    type = checkNotNull(storedTelemetryMetadata.envelopeType.serializedType)
                )
            }
        }.getOrNull()

    override fun clear() {
        fileStorageService.getStoredPayloads().forEach {
            fileStorageService.delete(it)
        }
    }
}
