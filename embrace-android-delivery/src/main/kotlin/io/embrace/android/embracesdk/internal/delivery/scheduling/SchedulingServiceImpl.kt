@file:Suppress("FunctionOnlyReturningConstant")

package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.storage.PayloadReference
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
    private val activeSends: MutableSet<PayloadReference> = Collections.synchronizedSet(HashSet())
    private val payloadsToRetry: MutableMap<PayloadReference, RetryInstance> = ConcurrentHashMap()

    override fun onPayloadIntake() {
        // When a payload arrives, check to see if there's already an active job try to deliver payloads
        // If not, schedule job. If so, do nothing.
        if (sendLoopActive.compareAndSet(false, true)) {
            schedulingWorker.submit {
                queueLoop()
            }
        }
    }

    override fun handleCrash(crashId: String) {
        // TODO: get ready to die
    }

    /**
     * Loop through the payloads ready to be sent by priority and queue for delivery
     */
    private fun queueLoop() {
        try {
            var readyPayloads = getReadyPayloads()
            while (readyPayloads.isNotEmpty() && readyToSend()) {
                readyPayloads.forEach { payload ->
                    queueSend(payload)
                }
                readyPayloads = getReadyPayloads()
            }
        } finally {
            sendLoopActive.set(false)
            scheduleNextCheck()
        }
    }

    private fun getReadyPayloads(): List<PayloadReference> {
        val payloads = storageService.getPayloadsByPriority()
        val readyPayloads = LinkedList<PayloadReference>()
        payloads.forEach { payload ->
            if (payload.shouldSendPayload()) {
                readyPayloads.push(payload)
            }
        }
        return readyPayloads
    }

    private fun PayloadReference.shouldSendPayload(): Boolean {
        // determine if the given payload is eligible to be sent
        // i.e. not already being sent, endpoint not blocked by 429, and isn't waiting to be retried
        updateBlockedEndpoint(endpoint)

        return if (activeSends.contains(this)) {
            false
        } else if (blockedEndpoints.containsKey(endpoint)) {
            false
        } else {
            payloadsToRetry[this]?.run {
                clock.now() >= nextRetryTimeMs
            } ?: true
        }
    }

    private fun queueSend(payload: PayloadReference): Future<ApiResponse> {
        activeSends.add(payload)
        return deliveryWorker.submit<ApiResponse> {
            executionService.attemptHttpRequest(
                payloadStream = payload.toInputStream(),
                envelopeType = payload.envelopeType
            ).apply {
                if (!shouldRetry) {
                    payloadsToRetry.remove(payload)
                    storageService.deletePayload(payload)
                } else {
                    val failedAttempts = payloadsToRetry[payload]?.let { it.failedAttempts + 1 } ?: 1
                    payloadsToRetry[payload] = RetryInstance(
                        failedAttempts = failedAttempts,
                        nextRetryTimeMs = calculateNextRetryTime(retryCount = failedAttempts - 1)
                    )
                }

                if (this is ApiResponse.TooManyRequests) {
                    retryAfter?.let { delayMs ->
                        blockedEndpoints[endpoint] = clock.now() + delayMs
                    }
                }

                activeSends.remove(payload)
            }
        }
    }

    private fun scheduleNextCheck() {
        payloadsToRetry.map { it.value.nextRetryTimeMs }.minOrNull()?.let { timestampMs ->
            if (timestampMs <= clock.now()) {
                onPayloadIntake()
            } else if (timestampMs != Long.MAX_VALUE) {
                schedulingWorker.schedule<Unit>(
                    ::onPayloadIntake,
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

    private fun PayloadReference.toInputStream(): () -> InputStream? = { storageService.loadPayloadAsStream(this) }

    private fun calculateDelay(nextRetryTimeMs: Long): Long = nextRetryTimeMs - clock.now()

    private fun calculateNextRetryTime(retryCount: Int): Long = clock.now() + (INITIAL_DELAY_MS * (1 shl retryCount))

    private data class RetryInstance(
        val failedAttempts: Int,
        val nextRetryTimeMs: Long
    )

    companion object {
        const val INITIAL_DELAY_MS = 60_000L
    }
}
