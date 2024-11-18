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

    private var lastSessionCacheAttempt: Future<*>? = null
    private var lastCacheSessionRef: StoredTelemetryMetadata? = null

    override fun shutdown() {
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        deliveryTracer?.onTake(metadata)
        val future = worker.submit(metadata) {
            processIntake(intake, metadata)
        }

        // cancel any cache attempts that are already pending to avoid unnecessary I/O.
        if (!metadata.complete && metadata.envelopeType == SupportedEnvelopeType.SESSION) {
            val prev = lastSessionCacheAttempt
            lastSessionCacheAttempt = future
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
            val lastCachedSession = lastCacheSessionRef

            if (metadata.complete) {
                schedulingService.onPayloadIntake()
            } else if (metadata.envelopeType == SupportedEnvelopeType.SESSION) {
                lastCacheSessionRef = metadata
            }

            // If the intake was for a session payload, delete the last cached session if it exists.
            // There should only ever be one cached session at one time.
            // If more than one thing is being cached, consider refactoring this so the clean up is managed
            // by the caching component.
            if (metadata.envelopeType == SupportedEnvelopeType.SESSION && lastCachedSession != null) {
                cacheStorageService.delete(lastCachedSession)
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.INTAKE_FAIL, exc)
        }
    }
}
