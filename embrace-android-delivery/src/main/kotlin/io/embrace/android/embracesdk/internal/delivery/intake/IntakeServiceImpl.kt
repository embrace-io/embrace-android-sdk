package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.storeAttachment
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val payloadStorageService: PayloadStorageService,
    private val cacheStorageService: PayloadStorageService,
    private val logger: InternalLogger,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val deliveryTracer: DeliveryTracer? = null,
    private val shutdownTimeoutMs: Long = 3000,
) : IntakeService {

    private val cachingTasks: MutableMap<SupportedEnvelopeType, Future<*>> = ConcurrentHashMap()
    private val lastCachedEntry: MutableMap<SupportedEnvelopeType, StoredTelemetryMetadata> = ConcurrentHashMap()

    override fun shutdown() {
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata?,
    ): Future<*> {
        deliveryTracer?.onTake(metadata)
        val future = worker.submit(metadata) {
            processIntake(
                intake = intake,
                metadata = metadata,
                staleEntry = staleEntry
            )
        }

        // cancel any cache attempts that are already pending to avoid unnecessary I/O.
        if (!metadata.complete) {
            val prev = cachingTasks[metadata.envelopeType]
            cachingTasks[metadata.envelopeType] = future
            prev?.cancel(false)
        }

        return future
    }

    @Suppress("UNCHECKED_CAST")
    private fun processIntake(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata?,
    ) {
        try {
            val service = when {
                metadata.complete -> payloadStorageService
                else -> cacheStorageService
            }
            service.store(metadata) { stream ->
                val type = metadata.envelopeType.serializedType
                if (type != null) {
                    serializer.toJson(intake, type, stream)
                } else { // payload doesn't require serialization
                    val pair = intake.data as Pair<String, ByteArray>
                    storeAttachment(stream, pair.second, pair.first)
                }
            }

            /**
             * Determine which cache entry to clean up:
             *
             * - Use [staleEntry] if caller explicitly tells the system that a particular payload will be stale after intake
             * - For complete payloads (i.e. ready for delivery), delete last cached entry for the given envelope type and don't replace it
             * - For incomplete payloads (i.e. cached snapshots not ready for delivery), delete last cached entry and replace it with intake
             */
            val entryToCleanup = staleEntry
                ?: if (metadata.complete) {
                    // Take the last cached entry and don't replace it
                    lastCachedEntry.remove(metadata.envelopeType)
                } else {
                    // Take the last cached entry and replace it with the new intake
                    lastCachedEntry.put(metadata.envelopeType, metadata)
                }

            if (metadata.complete) {
                deliveryTracer?.onPayloadIntake(metadata)
                schedulingService.onPayloadIntake()
            } else if (!cacheableEnvelopeTypes.contains(metadata.envelopeType)) {
                logger.trackInternalError(
                    InternalErrorType.INTAKE_UNEXPECTED_TYPE,
                    IllegalStateException("Unexpected envelope type cache attempt: ${metadata.envelopeType}"),
                )
            }

            entryToCleanup?.let {
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
