package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.util.concurrent.Future

class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val payloadStorageService: PayloadStorageService,
    private val cacheStorageService: PayloadStorageService,
    private val logger: EmbLogger,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val shutdownTimeoutMs: Long = 3000
) : IntakeService {

    private var lastCacheAttempt: Future<*>? = null
    private var lastCacheRef: StoredTelemetryMetadata? = null

    override fun shutdown() {
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        val future = worker.submit(metadata) {
            processIntake(intake, metadata)
        }

        // cancel any cache attempts that are already pending to avoid unnecessary I/O.
        if (!metadata.complete) {
            val prev = lastCacheAttempt
            lastCacheAttempt = future
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
            val last = lastCacheRef

            if (metadata.complete) {
                schedulingService.onPayloadIntake()
            } else {
                lastCacheRef = metadata
            }

            // delete any old cache attempts
            if (last != null) {
                cacheStorageService.delete(last)
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.INTAKE_FAIL, exc)
        }
    }
}
