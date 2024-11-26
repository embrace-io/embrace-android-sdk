package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val payloadStorageService: PayloadStorageService,
    private val cacheStorageService: PayloadStorageService,
    private val logger: EmbLogger,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val deliveryTracer: DeliveryTracer? = null,
    private val shutdownTimeoutMs: Long = 3000,
) : IntakeService {

    private val cachingTasks: MutableMap<SupportedEnvelopeType, Future<*>> = ConcurrentHashMap()
    private val cacheReferences: MutableMap<SupportedEnvelopeType, StoredTelemetryMetadata> = ConcurrentHashMap()

    override fun shutdown() {
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        deliveryTracer?.onTake(metadata)
        val future = worker.submit(metadata) {
            processIntake(intake, metadata)
        }

        // cancel any cache attempts that are already pending to avoid unnecessary I/O.
        if (!metadata.complete) {
            val prev = cachingTasks[metadata.envelopeType]
            cachingTasks[metadata.envelopeType] = future
            prev?.cancel(false)
        }
    }

    private fun processIntake(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
    ) {
        try {
            val service = when {
                metadata.complete -> payloadStorageService
                else -> cacheStorageService
            }
            service.store(metadata) { stream ->
                serializer.toJson(intake, metadata.envelopeType.serializedType, stream)
            }
            val lastReference = cacheReferences[metadata.envelopeType]

            if (metadata.complete) {
                schedulingService.onPayloadIntake()
                deliveryTracer?.onPayloadIntake(metadata)
            } else {
                cacheReferences[metadata.envelopeType] = metadata
                if (!cacheableEnvelopeTypes.contains(metadata.envelopeType)) {
                    logger.trackInternalError(
                        InternalErrorType.INTAKE_UNEXPECTED_TYPE,
                        IllegalStateException("Unexpected envelope type cache attempt: ${metadata.envelopeType}"),
                    )
                }
            }

            // Clean up any previously cached payload of the current type.
            // If the newly saved payload is complete, the cached copy is no longer needed. If it's a cache attempt,
            // the old copy is stale. Either way, it should be deleted.
            lastReference?.let {
                cacheStorageService.delete(it)
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.INTAKE_FAIL, exc)
        }
    }

    private companion object {
        private val cacheableEnvelopeTypes = listOf(SupportedEnvelopeType.SESSION, SupportedEnvelopeType.CRASH)
    }
}
