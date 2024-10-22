package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
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
    private val logger: EmbLogger,
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
        try {
            var deliveryQueue = createPayloadQueue()
            while (deliveryQueue.isNotEmpty() && readyToSend()) {
                deliveryQueue.poll()?.let { payload ->
                    if (payload.shouldSendPayload() && readyToSend()) {
                        payload.envelopeType.endpoint.updateBlockedEndpoint()
                        queueDelivery(payload)
                    }
                }

                if (queryForPayloads.compareAndSet(true, false) || deliveryQueue.isEmpty()) {
                    deliveryQueue = createPayloadQueue()
                }
            }
        } catch (t: Throwable) {
            logger.trackInternalError(InternalErrorType.UNKNOWN_DELIVERY_ERROR, t)
        } finally {
            sendLoopActive.set(false)
            scheduleNextDeliveryLoop()
        }
    }

    private fun createPayloadQueue() = LinkedList(
        storageService.getPayloadsByPriority()
            .filter { it.shouldSendPayload() }
            .sortedWith(storedTelemetryComparator)
    )

    private fun queueDelivery(payload: StoredTelemetryMetadata): Future<ApiResponse> {
        activeSends.add(payload)
        return deliveryWorker.submit<ApiResponse> {
            val response: ApiResponse =
                try {
                    payload.toStream()?.run {
                        executionService.attemptHttpRequest(
                            payloadStream = { this },
                            envelopeType = payload.envelopeType
                        )
                    } ?: ApiResponse.NoPayload
                } catch (t: Throwable) {
                    logger.trackInternalError(InternalErrorType.UNKNOWN_DELIVERY_ERROR, t)
                    ApiResponse.Incomplete(t)
                }

            with(response) {
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
                    val nextRetryTimeMs = if (this is ApiResponse.TooManyRequests && retryAfter != null) {
                        val unblockedTimestampMs = clock.now() + retryAfter
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
            response
        }
    }

    private fun scheduleNextDeliveryLoop() {
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
