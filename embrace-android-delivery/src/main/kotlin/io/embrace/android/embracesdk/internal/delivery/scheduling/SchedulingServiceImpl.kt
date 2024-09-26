@file:Suppress("FunctionOnlyReturningConstant")

package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.storage.StorageService2
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.InputStream
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class SchedulingServiceImpl(
    private val storageService: StorageService2,
    private val executionService: RequestExecutionService,
    private val schedulingWorker: BackgroundWorker,
    private val deliveryWorker: BackgroundWorker,
    private val clock: Clock
) : SchedulingService {

    private val blockedEndpoints: MutableMap<Endpoint, Long> = ConcurrentHashMap()
    private val sendLoopActive = AtomicBoolean(false)
    private val queryForPayloads = AtomicBoolean(true)
    private val activeSends: MutableSet<StoredTelemetryMetadata> = Collections.newSetFromMap(ConcurrentHashMap())
    private val payloadsToRetry: MutableMap<StoredTelemetryMetadata, RetryInstance> = ConcurrentHashMap()

    override fun onPayloadIntake() {
        queryForPayloads.set(true)
        startDeliveryLoop()
    }

    override fun handleCrash(crashId: String) {
        // TODO: get ready to die
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
            var deliveryQueue = getDeliveryQueue()
            while (deliveryQueue.isNotEmpty() && readyToSend()) {
                deliveryQueue.poll()?.let { payload ->
                    queueDelivery(payload)
                }

                if (queryForPayloads.compareAndSet(true, false)) {
                    deliveryQueue = getDeliveryQueue()
                }

                if (deliveryQueue.isEmpty()) {
                    deliveryQueue = getDeliveryQueue()
                }
            }
        } finally {
            sendLoopActive.set(false)
            scheduleNextCheck()
        }
    }

    private fun getDeliveryQueue() = LinkedList(getReadyPayloads())

    private fun getReadyPayloads(): List<StoredTelemetryMetadata> =
        storageService.getPayloadsByPriority()
            .filter { it.shouldSendPayload() }
            .sortedWith(storedTelemetryComparator)

    private fun StoredTelemetryMetadata.shouldSendPayload(): Boolean {
        // determine if the given payload is eligible to be sent
        // i.e. not already being sent, endpoint not blocked by 429, and isn't waiting to be retried
        updateBlockedEndpoint(envelopeType.endpoint)

        return if (activeSends.contains(this)) {
            false
        } else if (blockedEndpoints.containsKey(envelopeType.endpoint)) {
            false
        } else {
            payloadsToRetry[this]?.run {
                clock.now() >= nextRetryTimeMs
            } ?: true
        }
    }

    private fun queueDelivery(payload: StoredTelemetryMetadata): Future<ApiResponse> {
        activeSends.add(payload)
        return deliveryWorker.submit<ApiResponse> {
            try {
                val payloadStream = payload.toStream()
                if (payloadStream != null) {
                    executionService.attemptHttpRequest(
                        payloadStream = { payloadStream },
                        envelopeType = payload.envelopeType
                    ).apply {
                        if (!shouldRetry) {
                            // If the response is such that we should not ever retry the delivery of this payload,
                            // delete it from both the in memory retry payloads map and on disk
                            payloadsToRetry.remove(payload)
                            storageService.deletePayload(payload)
                        } else {
                            // If delivery of this payload should be retried, add or replace the entry in the retry map
                            // with the new values for how many times it has failed, and when the next retry should happen
                            val retryAttempts = payloadsToRetry[payload]?.failedAttempts ?: 0
                            val nextRetryTimeMs = calculateNextRetryTime(retryAttempts = retryAttempts)
                            payloadsToRetry[payload] = RetryInstance(
                                failedAttempts = retryAttempts + 1,
                                nextRetryTimeMs = nextRetryTimeMs
                            )
                        }

                        if (this is ApiResponse.TooManyRequests) {
                            retryAfter?.let { delayMs ->
                                blockedEndpoints[endpoint] = clock.now() + delayMs
                            }
                        }
                    }
                } else {
                    // Could not find payload. Do not retry.
                    ApiResponse.NoPayload
                }
            } finally {
                activeSends.remove(payload)
            }
        }
    }

    private fun scheduleNextCheck() {
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
        // TODO: determine if the SDK is in a state where it's ready to send payloads, i.e. have network connection and free thread
        return true
    }

    private fun updateBlockedEndpoint(endpoint: Endpoint) {
        blockedEndpoints[endpoint]?.let {
            if (it <= clock.now()) {
                blockedEndpoints.remove(endpoint)
            }
        }
    }

    private fun StoredTelemetryMetadata.toStream(): InputStream? = storageService.loadPayloadAsStream(this)

    private fun calculateDelay(nextRetryTimeMs: Long): Long = nextRetryTimeMs - clock.now()

    private fun calculateNextRetryTime(retryAttempts: Int): Long = clock.now() + (INITIAL_DELAY_MS * (1 shl retryAttempts))

    private data class RetryInstance(
        val failedAttempts: Int,
        val nextRetryTimeMs: Long
    )

    companion object {
        const val INITIAL_DELAY_MS = 60_000L
    }
}
