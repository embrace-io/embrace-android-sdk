package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.util.zip.GZIPOutputStream

internal class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val payloadStorageService: PayloadStorageService,
    private val internalErrorService: InternalErrorService,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    @Suppress("unused") private val storageLimit: Int = 100,
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
            payloadStorageService.store(metadata.filename) { outputStream ->
                GZIPOutputStream(outputStream).use { gzipStream ->
                    serializer.toJson(intake, metadata.envelopeType.serializedType, gzipStream)
                }
            }
            schedulingService.onPayloadIntake()
        } catch (exc: Throwable) {
            internalErrorService.handleInternalError(exc)
        }
    }
}
