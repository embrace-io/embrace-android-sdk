package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
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
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicReference

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

    // State machine for crash teardown. Transitions are one-way: ACTIVE → CRASH_RECEIVED → SEALED.
    // Once a crash-terminating payload (JVM_CRASH) is taken we stop the scheduler and
    // worker and only accept one final SESSION payload (persisted synchronously so the crash
    // session lands before the process dies).
    private val state = AtomicReference(State.ACTIVE)

    // Set if the current process has taken in a new log that signifies that it will end soon due to a JVM crash.
    // This value is checked later when we encounter a session part, which could be one that we are resurrecting and not the one
    // that is being ended by the JVM crash. In the case of the former, we process it normally. In the case of the latter,
    // we process it on the current thread (which should be the main thread) because we know it's going to crash anyway, and we
    // want to ensure that it is successfully persisted.
    @Volatile
    private var crashingProcessingIdentifier: String? = null

    override fun shutdown() {
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata?,
    ): Future<*> {
        deliveryTracer?.onTake(metadata)

        if (metadata.isCrashTerminatingProcess() && metadata.complete) {
            if (state.compareAndSet(State.ACTIVE, State.CRASH_RECEIVED)) {
                crashingProcessingIdentifier = metadata.processIdentifier
                schedulingService.shutdown()
                // non-blocking shutdown: reject subsequent submissions but defer the drain to
                // payloadStore.handleCrash's later intakeService.shutdown() call
                worker.shutdownAndWait(0)
                processIntake(intake, metadata, staleEntry)
                return immediateFuture()
            }
            return immediateFuture()
        }

        // The worker is shut down once a crash is detected, so anything arriving before the service is sealed must be persisted
        // synchronously (like resurrected session parts) or be unrecoverably loss.
        if (state.get() == State.CRASH_RECEIVED) {
            processIntake(intake, metadata, staleEntry)
            // Only seal the service if the payload is the crashing session's last session part, after which we take in no more
            // telemetry and let the process die.
            if (metadata.isCrashingPartForCurrentProcess()) {
                state.set(State.SEALED)
            }
            return immediateFuture()
        }

        if (state.get() != State.ACTIVE) {
            return immediateFuture()
        }

        val future = worker.submit(metadata) {
            processIntake(
                intake = intake,
                metadata = metadata,
                staleEntry = staleEntry,
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
                val envelopeSerializer = metadata.envelopeType.envelopeSerializer
                if (envelopeSerializer != null) {
                    serializer.toJson(intake, envelopeSerializer, stream)
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
                if (state.get() == State.ACTIVE) {
                    schedulingService.onPayloadIntake()
                }
            } else if (!cacheableEnvelopeTypes.contains(metadata.envelopeType)) {
                logger.trackInternalError(
                    InternalErrorType.IntakeUnexpectedType,
                    IllegalStateException("Unexpected envelope type cache attempt: ${metadata.envelopeType}"),
                )
            }

            entryToCleanup?.let {
                cacheStorageService.delete(it)
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.IntakeFail, exc)
        }
    }

    private fun immediateFuture(): Future<*> = FutureTask { }.apply { run() }

    private fun StoredTelemetryMetadata.isCrashTerminatingProcess(): Boolean =
        payloadType == PayloadType.JVM_CRASH || payloadType == PayloadType.REACT_NATIVE_CRASH

    /**
     * Returns true if the metadata is for a session part payload for the current process that is crashing.
     */
    private fun StoredTelemetryMetadata.isCrashingPartForCurrentProcess(): Boolean =
        envelopeType == SESSION && complete && processIdentifier == crashingProcessingIdentifier

    private enum class State {
        ACTIVE,
        CRASH_RECEIVED,
        SEALED,
    }

    private companion object {
        private val cacheableEnvelopeTypes = listOf(SESSION, CRASH)
    }
}
