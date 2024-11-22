package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.InputStream
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SchedulingServiceImpl(
    private val storageService: PayloadStorageService,
    private val executionService: RequestExecutionService,
    private val schedulingWorker: BackgroundWorker,
    private val deliveryWorker: BackgroundWorker,
    private val clock: Clock,
    private val logger: EmbLogger
) : SchedulingService {

    private val blockedEndpoints: MutableMap<Endpoint, Long> = ConcurrentHashMap()
    private val hasNetwork = AtomicBoolean(true)
    private val sendLoopActive = AtomicBoolean(false)
    private val queryForPayloads = AtomicBoolean(true)
    private val activeSends: MutableSet<StoredTelemetryMetadata> = Collections.newSetFromMap(ConcurrentHashMap())
    private val deleteInProgress: MutableSet<StoredTelemetryMetadata> = Collections.newSetFromMap(ConcurrentHashMap())
    private val payloadsToRetry: MutableMap<StoredTelemetryMetadata, RetryInstance> = ConcurrentHashMap()

    override fun onPayloadIntake() {
        queryForPayloads.set(true)
        startDeliveryLoop()
    }

    override fun shutdown() {
        // shutdown workers from further scheduling but don't wait for completion as
        // we can just retry in the next process
        schedulingWorker.shutdownAndWait(0)
        deliveryWorker.shutdownAndWait(0)
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        val currentlyConnected = status.isReachable

        // Set a new connection status if it differs from the current one
        if (currentlyConnected != hasNetwork.get()) {
            hasNetwork.set(currentlyConnected)
            // trigger a new delivery loop we went from being offline to being connected
            if (currentlyConnected) {
                startDeliveryLoop()
            }
        }
    }

    private fun startDeliveryLoop() {
        // When a payload arrives, check to see if there's already an active job try to deliver payloads
        // If not, schedule job. If so, do nothing.
        if (sendLoopActive.compareAndSet(false, true)) {
            schedulingWorker.submit {
                deliveryLoop()
            }
        }
    }

    /**
     * Loop through the payloads ready to be sent by priority and queue for delivery
     */
    private fun deliveryLoop() {
        val failedPayloads = mutableSetOf<StoredTelemetryMetadata>()
        try {
            var deliveryQueue = createPayloadQueue()
            while (deliveryQueue.isNotEmpty() && readyToSend()) {
                val payload = deliveryQueue.poll()
                runCatching {
                    payload?.run {
                        if (shouldSendPayload() && readyToSend()) {
                            envelopeType.endpoint.updateBlockedEndpoint()
                            queueDelivery(this)
                        }
                    }
                }.exceptionOrNull()?.let { error ->
                    // This block catches unhandled errors resulting a single payload failing to be queued for delivery.
                    // Any payload failed to be queued will be bypassed in the current delivery loop cycle, as the
                    // SDK encountered an error as it tried to determine whether the payload should be delivered.
                    val fileName = payload?.run {
                        // Keeping track of the payloads that failed to be queued prevents an infinite loop where a
                        // payload that refused to be queued successfully keeps being retried in the same loop.
                        // So even if it isn't possible now, this code prevents makes an attempt to prevent it from
                        // happening in the future, if, say, checking the state of the SDK can throw indefinitely.
                        failedPayloads.add(this)
                        filename
                    }
                    logger.trackInternalError(
                        type = InternalErrorType.DELIVERY_SCHEDULING_FAIL,
                        throwable = IllegalStateException("Failed to queue payload with file name $fileName", error)
                    )
                }

                if (queryForPayloads.compareAndSet(true, false) || deliveryQueue.isEmpty()) {
                    deliveryQueue = createPayloadQueue(failedPayloads)
                }
            }
        } catch (t: Throwable) {
            // This block catches unhandled errors resulting from the recreation of a queue of payloads to be delivered
            // When this type of error encountered, we abort the delivery loop and wait for the next retry or intake
            // to retry any pending payloads.
            logger.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, t)
        } finally {
            sendLoopActive.set(false)
            scheduleDeliveryLoopForNextRetry()
        }
    }

    private fun createPayloadQueue(exclude: Set<StoredTelemetryMetadata> = emptySet()) = LinkedList(
        storageService.getPayloadsByPriority()
            .filter { it.shouldSendPayload() && !exclude.contains(it) }
            .sortedWith(storedTelemetryComparator)
    )

    private fun queueDelivery(payload: StoredTelemetryMetadata): Future<ExecutionResult> {
        activeSends.add(payload)
        return deliveryWorker.submit<ExecutionResult> {
            val result: ExecutionResult =
                try {
                    // If fail to convert metadata to stream, we can't expect it will success later, so we won't retry.
                    // The storage service will log an internal exception and we move on.
                    payload.toStream()?.run {
                        executionService.attemptHttpRequest(
                            payloadStream = { this },
                            envelopeType = payload.envelopeType,
                            payloadType = payload.payloadType.value
                        )
                    } ?: ExecutionResult.NotAttempted
                } catch (t: Throwable) {
                    // An unknown error occurred, not the expected exceptions during request executions
                    // These types of errors happen before we execute the request, and results in us unable to
                    // turn the stored bytes in to a request that can be executed.
                    // For this, we log the error to ensure it's not a systemic problem and move on from the payload.
                    logger.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, t)
                    ExecutionResult.Incomplete(exception = t, retry = false)
                }

            with(result) {
                if (!shouldRetry) {
                    // If the response is such that we should not ever retry the delivery of this payload,
                    // delete it from both the in memory retry payloads map and on disk
                    payloadsToRetry.remove(payload)
                    deleteInProgress.add(payload)
                    storageService.delete(payload) {
                        deleteInProgress.remove(payload)
                    }
                } else {
                    // If delivery of this payload should be retried, add or replace the entry in the retry map
                    // with the new values for how many times it has failed, and when the next retry should happen
                    val retryAttempts = payloadsToRetry[payload]?.failedAttempts ?: 0
                    val nextRetryTimeMs = if (this is ExecutionResult.TooManyRequests && retryAfterMs != null) {
                        val unblockedTimestampMs = clock.now() + retryAfterMs
                        blockedEndpoints[endpoint] = unblockedTimestampMs
                        unblockedTimestampMs + 1L
                    } else {
                        calculateNextRetryTime(retryAttempts = retryAttempts)
                    }

                    payloadsToRetry[payload] = RetryInstance(
                        failedAttempts = retryAttempts + 1,
                        nextRetryTimeMs = nextRetryTimeMs
                    )
                }
            }
            activeSends.remove(payload)
            result
        }
    }

    private fun scheduleDeliveryLoopForNextRetry() {
        payloadsToRetry.map { it.value.nextRetryTimeMs }.minOrNull()?.let { timestampMs ->
            if (timestampMs <= clock.now()) {
                startDeliveryLoop()
            } else if (timestampMs != Long.MAX_VALUE) {
                schedulingWorker.schedule<Unit>(
                    ::startDeliveryLoop,
                    calculateDelay(timestampMs),
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    private fun readyToSend(): Boolean {
        return hasNetwork.get()
    }

    private fun StoredTelemetryMetadata.shouldSendPayload(): Boolean {
        // determine if the given payload is eligible to be sent
        // i.e. not already being sent, endpoint not blocked by 429, and isn't waiting to be retried
        return if (activeSends.contains(this) || deleteInProgress.contains(this)) {
            false
        } else if (isEndpointBlocked()) {
            false
        } else {
            payloadsToRetry[this]?.run {
                clock.now() >= nextRetryTimeMs
            } ?: true
        }
    }

    private fun StoredTelemetryMetadata.toStream(): InputStream? = storageService.loadPayloadAsStream(this)

    private fun StoredTelemetryMetadata.isEndpointBlocked(): Boolean =
        blockedEndpoints[envelopeType.endpoint]?.let { timestampMs ->
            timestampMs > clock.now()
        } ?: false

    private fun Endpoint.updateBlockedEndpoint() {
        blockedEndpoints[this]?.let {
            if (it <= clock.now()) {
                blockedEndpoints.remove(this)
            }
        }
    }

    private fun calculateDelay(nextRetryTimeMs: Long): Long = nextRetryTimeMs - clock.now()

    /**
     * Note: bit-shifting is used to raise 2 to the power of [retryAttempts]. This is the most efficient way of
     * doing this, and as much as it pains me to do this, it's isolated and tested, and the runtime penalty, however
     * tiny, is not worth incurring if we can instead do this.
     */
    private fun calculateNextRetryTime(
        retryAttempts: Int,
    ): Long = clock.now() + (INITIAL_DELAY_MS * (1 shl retryAttempts))

    private data class RetryInstance(
        val failedAttempts: Int,
        val nextRetryTimeMs: Long,
    )

    companion object {
        const val INITIAL_DELAY_MS = 60_000L
    }
}
