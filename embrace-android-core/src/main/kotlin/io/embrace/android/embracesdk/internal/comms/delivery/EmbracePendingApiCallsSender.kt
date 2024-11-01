package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.api.limiter
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

private const val RETRY_PERIOD = 120L // In seconds
private const val MAX_EXPONENTIAL_RETRY_PERIOD = 3600 // In seconds

internal class EmbracePendingApiCallsSender(
    private val worker: BackgroundWorker,
    private val cacheManager: DeliveryCacheManager,
    private val clock: Clock,
) : PendingApiCallsSender {

    private val pendingApiCallQueue: PendingApiCallQueue by lazy {
        cacheManager.loadPendingApiCallQueue()
    }
    private var lastDeliveryTask: ScheduledFuture<*>? = null
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN
    private val sendMethodRef: AtomicReference<SendMethod?> = AtomicReference(null)

    override fun initializeRetrySchedule(sendMethod: SendMethod) {
        sendMethodRef.set(sendMethod)
        worker.submit(this::scheduleApiCallsDelivery)
    }

    override fun savePendingApiCall(request: ApiRequest, action: SerializationAction, sync: Boolean) {
        // Save the payload to disk.
        val cachedPayloadName = cacheManager.savePayload(action, sync)

        // Save the pending api calls to disk.
        val pendingApiCall = PendingApiCall(request, cachedPayloadName, clock.now())
        pendingApiCallQueue.add(pendingApiCall)
        cacheManager.savePendingApiCallQueue(pendingApiCallQueue, sync)
    }

    override fun scheduleRetry(response: ApiResponse) {
        when (response) {
            is ApiResponse.Incomplete -> {
                scheduleApiCallsDelivery(RETRY_PERIOD)
            }

            is ApiResponse.TooManyRequests -> {
                with(response.endpoint.limiter) {
                    updateRateLimitStatus()
                    scheduleRetry(
                        worker,
                        response.retryAfter,
                        this@EmbracePendingApiCallsSender::scheduleApiCallsDelivery
                    )
                }
            }

            else -> {
                // Not expected, shouldRetry() should be called before scheduleForRetry().
            }
        }
    }

    /**
     * Called when the network status has changed.
     */
    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        lastNetworkStatus = status
        if (status.isReachable) {
            scheduleApiCallsDelivery()
        } else {
            synchronized(this) {
                lastDeliveryTask?.let { task ->
                    if (task.cancel(false)) {
                        lastDeliveryTask = null
                    }
                }
            }
        }
    }

    /**
     * Returns true if there is an active pending retry task
     */
    fun isDeliveryTaskActive(): Boolean =
        lastDeliveryTask?.let { task ->
            !task.isCancelled && !task.isDone
        } ?: false

    /**
     * Return true if the conditions are met for a delivery task to be scheduled
     */
    private fun shouldScheduleDelivery(): Boolean {
        return !isDeliveryTaskActive() && pendingApiCallQueue.hasPendingApiCallsToSend()
    }

    /**
     * Schedules an action to send pending API calls. If it doesn't send all the pending API requests, it will recursively schedule
     * itself with an exponential backoff delay, starting with [RETRY_PERIOD], doubling after that until
     * [MAX_EXPONENTIAL_RETRY_PERIOD] is reached, after which case it stops trying until the next cold start.
     */
    private fun scheduleApiCallsDelivery(delayInSeconds: Long = 0L) {
        synchronized(this) {
            if (shouldScheduleDelivery()) {
                lastDeliveryTask = worker.schedule<Unit>(
                    { executeDelivery(delayInSeconds) },
                    delayInSeconds,
                    TimeUnit.SECONDS
                )
            }
        }
    }

    /**
     * Sends all pending API calls.
     */
    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun executeDelivery(delayInSeconds: Long) {
        if (!lastNetworkStatus.isReachable) {
            return
        }

        sendMethodRef.get()?.let { sendMethod ->
            runCatching {
                val failedApiCallsToRetry = mutableListOf<PendingApiCall>()
                var applyExponentialBackoff = false

                while (true) {
                    val pendingApiCall = pendingApiCallQueue.pollNextPendingApiCall() ?: break
                    val response = sendPendingApiCall(sendMethod, pendingApiCall)
                    response.let {
                        val url = EmbraceUrl.create(pendingApiCall.apiRequest.url.url)
                        clearRateLimitIfApplies(url.endpoint(), response)

                        if (response.shouldRetry) {
                            when (response) {
                                is ApiResponse.TooManyRequests -> {
                                    with(response.endpoint.limiter) {
                                        updateRateLimitStatus()
                                        scheduleRetry(
                                            worker,
                                            response.retryAfter,
                                            this@EmbracePendingApiCallsSender::scheduleApiCallsDelivery
                                        )
                                    }
                                }

                                is ApiResponse.Incomplete -> {
                                    applyExponentialBackoff = true
                                }

                                else -> {
                                    // Not expected
                                }
                            }
                            // Should retry, so we add it back to the queue.
                            failedApiCallsToRetry.add(pendingApiCall)
                        } else {
                            // Shouldn't retry, so delete the payload and save the pending api calls.
                            cacheManager.deletePayload(pendingApiCall.cachedPayloadFilename)
                            cacheManager.savePendingApiCallQueue(pendingApiCallQueue)
                        }
                    }
                }

                // Add back to the queue all retries that failed.
                failedApiCallsToRetry.forEach {
                    pendingApiCallQueue.add(it)
                }

                if (pendingApiCallQueue.hasPendingApiCallsToSend()) {
                    worker.submit {
                        scheduleNextApiCallsDelivery(
                            applyExponentialBackoff,
                            delayInSeconds
                        )
                    }
                }
            }
        }
    }

    /**
     * Send the request for a PendingApiCall.
     */
    private fun sendPendingApiCall(sendMethod: SendMethod, call: PendingApiCall): ApiResponse {
        // If payload is null, the file could have been removed. We don't have to retry this call.
        val payload: SerializationAction = cacheManager.loadPayloadAsAction(call.cachedPayloadFilename)
        return sendMethod(call.apiRequest, payload)
    }

    /**
     * Schedules the next call to retry sending the pending api calls again.
     * If [applyExponentialBackoff] is true, it will double the delay time, up to a maximum of
     * [MAX_EXPONENTIAL_RETRY_PERIOD].
     */
    private fun scheduleNextApiCallsDelivery(applyExponentialBackoff: Boolean, delay: Long) {
        val nextDelay = if (applyExponentialBackoff) {
            max(RETRY_PERIOD, delay * 2)
        } else {
            RETRY_PERIOD
        }
        if (nextDelay <= MAX_EXPONENTIAL_RETRY_PERIOD) {
            scheduleApiCallsDelivery(nextDelay)
        }
    }

    /**
     * Clears the rate limit for the given endpoint if the response is not a rate limit response.
     */
    private fun clearRateLimitIfApplies(endpoint: Endpoint, response: ApiResponse) {
        if (response !is ApiResponse.TooManyRequests) {
            endpoint.limiter.clearRateLimit()
        }
    }
}

typealias SendMethod = (request: ApiRequest, action: SerializationAction) -> ApiResponse
