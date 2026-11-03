package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.StorageOutcome
import io.embrace.android.embracesdk.internal.delivery.storage.storeAttachment
import io.embrace.android.embracesdk.internal.delivery.traceSection
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.CountingOutputStream
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import io.embrace.android.embracesdk.internal.utils.TraceCounter
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicReference

class IntakeServiceImpl(
    private val schedulingService: SchedulingService,
    private val payloadStorageService: PayloadStorageService,
    private val cacheStorageService: PayloadStorageService,
    private val errorHandler: ErrorHandler,
    private val serializer: PlatformSerializer,
    private val worker: PriorityWorker<StoredTelemetryMetadata>,
    private val deliveryTracer: DeliveryTracer? = null,
    private val shutdownTimeoutMs: Long = 3000,
) : IntakeService {

    /**
     * The bytes handed to storage, counted before storage compresses them.
     */
    private val serializedBytes = TraceCounter("sf-bytes-serialized")

    // The pending and last cached payload of each envelope type, which a newer cached payload of that type supersedes.
    // This assumes cached payloads come only from the current process, so a session snapshot is always of the current
    // session part, and a snapshot of a newer part makes any older one redundant because that part's complete payload
    // has already been taken. Anything that caches snapshots of other parts, such as parts read back from disk, would
    // delete the snapshots it doesn't supersede, so it must take them in complete instead.
    private val cachingTasks: MutableMap<SupportedEnvelopeType, Future<*>> = ConcurrentHashMap()
    private val lastCachedEntry: MutableMap<SupportedEnvelopeType, StoredTelemetryMetadata> = ConcurrentHashMap()

    // State machine for crash teardown. Transitions are one-way: ACTIVE → CRASH_RECEIVED → SEALED.
    // Once a crash-terminating payload is taken we stop the scheduler and worker, and persist every complete payload
    // synchronously so it lands before the process dies. The crash teardown ends with [shutdown], which seals the service.
    private val state = AtomicReference(State.ACTIVE)

    override fun shutdown() {
        // The crash teardown ends here, so reject any later intake rather than store it synchronously on the caller's
        // thread in a process that survives the crash.
        state.compareAndSet(State.CRASH_RECEIVED, State.SEALED)
        worker.shutdownAndWait(shutdownTimeoutMs)
    }

    override fun take(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata?,
        onStored: (() -> Unit)?,
    ): Future<IntakeResult> {
        deliveryTracer?.onTake(metadata)

        if (metadata.isCrashTerminatingProcess() && metadata.complete) {
            if (state.compareAndSet(State.ACTIVE, State.CRASH_RECEIVED)) {
                schedulingService.shutdown()
                // A snapshot still queued on the worker would run after the crash, possibly after the complete crashing part,
                // so cancel it. One that is already being written can't be stopped.
                cachingTasks.values.forEach { worker.cancelAndRemove(it) }
                // non-blocking shutdown: reject subsequent submissions but defer the drain to
                // payloadStore.handleCrash's later intakeService.shutdown() call
                worker.shutdownAndWait(0)
                return immediateFuture(processIntake(intake, metadata, staleEntry, onStored))
            }
            return immediateFuture(IntakeResult.NOT_ATTEMPTED)
        }

        // The worker is shut down once a crash is detected, so a complete payload arriving before the service is sealed must be
        // persisted synchronously (like resurrected session parts) or be unrecoverably lost. A snapshot is rejected: the
        // crashing part is either taken complete or written in place, and a snapshot of it that lands after it would be
        // resurrected as a duplicate on the next launch.
        if (state.get() == State.CRASH_RECEIVED) {
            if (!metadata.complete) {
                return immediateFuture(IntakeResult.NOT_ATTEMPTED)
            }
            return immediateFuture(processIntake(intake, metadata, staleEntry, onStored))
        }

        if (state.get() != State.ACTIVE) {
            return immediateFuture(IntakeResult.NOT_ATTEMPTED)
        }

        val future = worker.submit(
            priorityInfo = metadata,
            callable = Callable {
                processIntake(
                    intake = intake,
                    metadata = metadata,
                    staleEntry = staleEntry,
                    onStored = onStored,
                )
            },
        )

        // cancel any cache attempts that are already pending to avoid unnecessary I/O, and evict
        // from the queue so superseded envelopes aren't retained until the worker drains the runnable
        if (!metadata.complete) {
            val prev = cachingTasks[metadata.envelopeType]
            cachingTasks[metadata.envelopeType] = future
            prev?.let { worker.cancelAndRemove(it) }
        }

        return future
    }

    /**
     * Stores the payload and returns the [IntakeResult]. An attempt that throws before storage reports an outcome is
     * [IntakeResult.NOT_ATTEMPTED], while one that throws after the payload is on disk is still [IntakeResult.STORED].
     */
    @Suppress("UNCHECKED_CAST")
    private fun processIntake(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata?,
        onStored: (() -> Unit)?,
    ): IntakeResult {
        var result = IntakeResult.NOT_ATTEMPTED
        errorHandler.guardSdkCode(SdkErrorType.IntakeFail) {
            val service = when {
                metadata.complete -> payloadStorageService
                else -> cacheStorageService
            }
            val outcome = SystemTrace.trace(metadata.traceSection("intake-process")) {
                service.store(metadata) { stream ->
                    val counted = CountingOutputStream(stream)
                    val envelopeSerializer = metadata.envelopeType.envelopeSerializer
                    if (envelopeSerializer != null) {
                        SystemTrace.trace(metadata.traceSection("payload-json-serialize")) {
                            serializer.toJson(intake, envelopeSerializer, counted)
                        }
                    } else { // payload doesn't require serialization
                        val pair = intake.data as Pair<String, ByteArray>
                        storeAttachment(counted, pair.second, pair.first)
                    }
                    serializedBytes.add(counted.written)
                }
            }

            // Return non-successful outcomes early.
            // A payload storage rejected may succeed in a later attempt, but one that failed won't.
            result = when (outcome) {
                StorageOutcome.REJECTED -> IntakeResult.RETRYABLE_FAILURE
                StorageOutcome.FAILED -> IntakeResult.PERMANENT_FAILURE
                StorageOutcome.STORED -> IntakeResult.STORED
            }
            if (result != IntakeResult.STORED) {
                return@guardSdkCode
            }

            // the payload is now on disk, so any other copy the caller holds is safe to discard
            errorHandler.guardSdkCode(SdkErrorType.IntakeFail) {
                onStored?.invoke()
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
                errorHandler.trackSdkError(
                    SdkErrorType.IntakeUnexpectedType,
                    IllegalStateException("Unexpected envelope type cache attempt: ${metadata.envelopeType}"),
                )
            }

            entryToCleanup?.let {
                cacheStorageService.delete(it)
            }
        }
        return result
    }

    private fun immediateFuture(value: IntakeResult): Future<IntakeResult> = FutureTask { value }.apply { run() }

    private fun StoredTelemetryMetadata.isCrashTerminatingProcess(): Boolean =
        payloadType == PayloadType.JVM_CRASH || payloadType == PayloadType.REACT_NATIVE_CRASH

    private enum class State {
        ACTIVE,
        CRASH_RECEIVED,
        SEALED,
    }

    private companion object {
        private val cacheableEnvelopeTypes = listOf(SESSION, CRASH)
    }
}
