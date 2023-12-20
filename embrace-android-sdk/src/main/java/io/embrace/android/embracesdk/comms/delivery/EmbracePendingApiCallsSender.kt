package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logger
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class EmbracePendingApiCallsSender(
    networkConnectivityService: NetworkConnectivityService,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val cacheManager: DeliveryCacheManager,
    private val rateLimitHandler: RateLimitHandler,
    private val clock: Clock,
) : PendingApiCallsSender, NetworkConnectivityListener {

    private val pendingApiCalls: PendingApiCalls =
        cacheManager.loadPendingApiCalls().also {
            it.setRateLimitHandler(rateLimitHandler)
        }
    private var lastDeliveryTask: ScheduledFuture<*>? = null
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN
    private lateinit var sendMethod: (request: ApiRequest, payload: ByteArray) -> ApiResponse

    init {
        logger.logDeveloper(TAG, "Starting DeliveryRetryManager")
        networkConnectivityService.addNetworkConnectivityListener(this)
        lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
        scheduledExecutorService.submit(this::scheduleApiCallsDelivery)
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> ApiResponse) {
        this.sendMethod = sendMethod
    }

    override fun savePendingApiCall(request: ApiRequest, payload: ByteArray): PendingApiCall {
        // Save the payload to disk.
        val cachedPayloadName = cacheManager.savePayload(payload)

        // Save the pending api call to disk.
        val pendingApiCall = PendingApiCall(request, cachedPayloadName, clock.now())
        pendingApiCalls.add(pendingApiCall)
        cacheManager.savePendingApiCalls(pendingApiCalls)
        return pendingApiCall
    }

    override fun scheduleRetry(response: ApiResponse) {
        logger.logDeveloper(TAG, "Scheduling api call for retry")

        when (response) {
            is ApiResponse.Incomplete -> {
                scheduleApiCallsDelivery(RETRY_PERIOD)
            }
            is ApiResponse.TooManyRequests -> {
                rateLimitHandler.setRateLimitAndScheduleRetry(
                    response.endpoint,
                    response.retryAfter,
                    this::scheduleApiCallsDelivery
                )
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
                        logger.logDebug("Api Calls Delivery Action was stopped because there is no connection. ")
                        lastDeliveryTask = null
                    } else {
                        logger.logError("Api Calls Delivery Action could not be stopped.")
                    }
                }
            }
        }
    }

    /**
     * Returns true if there is an active pending retry task
     */
    fun isDeliveryTaskActive(): Boolean = lastDeliveryTask?.let { task ->
        !task.isCancelled && !task.isDone
    } ?: false

    /**
     * Return true if the conditions are met for a delivery task to be scheduled
     */
    private fun shouldScheduleDelivery(): Boolean {
        return !isDeliveryTaskActive() && pendingApiCalls.hasPendingApiCallsToSend()
    }

    /**
     * Schedules an action to send pending API calls. If it doesn't send all the pending API requests, it will recursively schedule
     * itself with an exponential backoff delay, starting with [RETRY_PERIOD], doubling after that until
     * [MAX_EXPONENTIAL_RETRY_PERIOD] is reached, after which case it stops trying until the next cold start.
     */
    private fun scheduleApiCallsDelivery(delayInSeconds: Long = 0L) {
        try {
            synchronized(this) {
                if (shouldScheduleDelivery()) {
                    lastDeliveryTask = scheduledExecutorService.schedule(
                        { executeDelivery(delayInSeconds) },
                        delayInSeconds,
                        TimeUnit.SECONDS
                    )
                    logger.logInfo(
                        "Scheduled failed API calls to retry ${if (delayInSeconds == 0L) "now" else "in $delayInSeconds seconds"}"
                    )
                }
            }
        } catch (e: RejectedExecutionException) {
            // This happens if the executor has shutdown previous to the schedule call
            logger.logError("Cannot schedule delivery of pending api calls.", e)
        }
    }

    /**
     * Sends all pending API calls.
     */
    private fun executeDelivery(delayInSeconds: Long) {
        if (!lastNetworkStatus.isReachable) {
            logger.logInfo("Did not retry api calls as scheduled because network is not reachable")
            return
        }
        try {
            logger.logDeveloper(TAG, "Sending Pending API calls")

            val failedApiCallsToRetry = mutableListOf<PendingApiCall>()
            var applyExponentialBackoff = false

            while (true) {
                val pendingApiCall = pendingApiCalls.pollNextPendingApiCall() ?: break
                val response = sendPendingApiCall(pendingApiCall)
                response?.let {
                    clearRateLimitIfApplies(pendingApiCall.apiRequest.url.endpoint(), response)

                    if (response.shouldRetry) {
                        when (response) {
                            is ApiResponse.TooManyRequests -> {
                                rateLimitHandler.setRateLimitAndScheduleRetry(
                                    response.endpoint,
                                    response.retryAfter,
                                    this::scheduleApiCallsDelivery
                                )
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
                        cacheManager.savePendingApiCalls(pendingApiCalls)
                    }
                }
            }

            // Add back to the queue all retries that failed.
            failedApiCallsToRetry.forEach {
                pendingApiCalls.add(it)
            }

            if (pendingApiCalls.hasPendingApiCallsToSend()) {
                scheduledExecutorService.submit {
                    scheduleNextApiCallsDelivery(
                        applyExponentialBackoff,
                        delayInSeconds
                    )
                }
            }
        } catch (ex: Exception) {
            logger.logDebug("Error when sending API call", ex)
        }
    }

    /**
     * Send the request for a PendingApiCall.
     */
    private fun sendPendingApiCall(call: PendingApiCall): ApiResponse? {
        val payload = cacheManager.loadPayload(call.cachedPayloadFilename)

        if (payload == null) {
            // If payload is null, the file could have been removed. We don't have to retry this call.
            logger.logDeveloper(TAG, "Could not retrieve cached api payload")
            return null
        }
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
            rateLimitHandler.clearRateLimit(endpoint)
        }
    }
}

private const val TAG = "EmbracePendingApiCallsSender"
private const val RETRY_PERIOD = 120L // In seconds
private const val MAX_EXPONENTIAL_RETRY_PERIOD = 3600 // In seconds
