package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.worker.PriorityWorker

internal class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val payloadStorageService: PayloadStorageService,
    private val internalErrorService: InternalErrorService,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val shutdownTimeoutMs: Long = 3000
) : IntakeService {

    override fun handleCrash(crashId: String) {
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        worker.submit(metadata) {
            processIntake(intake, metadata)
        }
    }

    private fun processIntake(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        try {
            payloadStorageService.store(metadata) { stream ->
                serializer.toJson(intake, metadata.envelopeType.serializedType, stream)
            }
            schedulingService.onPayloadIntake()
        } catch (exc: Throwable) {
            internalErrorService.handleInternalError(exc)
        }
    }
}
