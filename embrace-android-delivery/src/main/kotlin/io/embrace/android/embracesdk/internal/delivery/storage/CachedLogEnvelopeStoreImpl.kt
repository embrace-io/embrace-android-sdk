package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
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
    private val errorHandler: ErrorHandler,
    private val serializer: PlatformSerializer,
    clock: Clock,
    storageLimit: Int = 100,
) : CachedLogEnvelopeStore {

    private val fileStorageService: FileStorageService = FileStorageServiceImpl(
        outputDir,
        worker,
        errorHandler,
        clock,
        storageLimit,
    )

    override fun create(
        storedTelemetryMetadata: StoredTelemetryMetadata,
        resource: EnvelopeResource,
        metadata: EnvelopeMetadata,
    ) {
        fileStorageService.store(storedTelemetryMetadata) { stream ->
            serializer.toJson(
                LogPayload().createLogEnvelope(resource, metadata),
                Envelope.serializer(LogPayload.serializer()),
                stream,
            )
        }
    }

    override fun get(storedTelemetryMetadata: StoredTelemetryMetadata): Envelope<LogPayload>? =
        errorHandler.guardSdkCode(SdkErrorType.PayloadStorageFail, null) {
            fileStorageService.loadPayloadAsStream(storedTelemetryMetadata)?.let { inputStream ->
                serializer.fromJson(inputStream, Envelope.serializer(LogPayload.serializer()))
            }
        }

    override fun clear() {
        fileStorageService.getStoredPayloads().forEach {
            fileStorageService.delete(it)
        }
    }
}
