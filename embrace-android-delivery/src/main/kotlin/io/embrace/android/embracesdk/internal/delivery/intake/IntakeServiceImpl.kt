package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

internal class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val worker: BackgroundWorker,
    private val serializer: PlatformSerializer
) : IntakeService {

    companion object {
        private const val SHUTDOWN_TIMEOUT_MS = 3000L
    }

    private val storageLock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        worker.submit {
            try {
                pruneStorageIfNeeded()
                intake.storeTelemetry(metadata)
                schedulingService.onPayloadIntake()
            } catch (exc: Throwable) {
                // TODO: error handling - call telemetry service?
            }
        }
    }

    private fun pruneStorageIfNeeded() {
        if (isStorageLimitExceeded()) { // double-checked lock
            storageLock.write { // TODO: is the lock necessary?
                if (isStorageLimitExceeded()) {
                    pruneStorage()
                }
            }
        }
    }

    private fun pruneStorage() {
        TODO("Not yet implemented")
    }

    private fun isStorageLimitExceeded(): Boolean {
        TODO()
    }

    private fun <T> Envelope<T>.storeTelemetry(metadata: StoredTelemetryMetadata) {
        storageLock.write {
            val dst = getStorageLocation(metadata)
            dst.outputStream().buffered().use { stream -> // TODO: compress + lock?
                serializer.toJson(this, metadata.envelopeType.serializedType, stream)
            }
        }
    }

    private fun getStorageLocation(metadata: StoredTelemetryMetadata): File {
        // TODO: consider abstracting file away completely from this layer? use
        //  StoredTelemetryMetadata instead?
        TODO()
    }

    override fun handleCrash(crashId: String) {
        worker.shutdownAndWait(SHUTDOWN_TIMEOUT_MS)
    }

}
