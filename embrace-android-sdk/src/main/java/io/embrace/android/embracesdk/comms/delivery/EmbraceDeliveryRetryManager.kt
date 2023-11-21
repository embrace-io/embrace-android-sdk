package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logger
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class EmbraceDeliveryRetryManager(
    networkConnectivityService: NetworkConnectivityService,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val cacheManager: DeliveryCacheManager
) : DeliveryRetryManager, NetworkConnectivityListener {

    private val retryQueue: DeliveryFailedApiCalls by lazy { cacheManager.loadFailedApiCalls() }
    private var lastRetryTask: ScheduledFuture<*>? = null
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN
    private lateinit var retryMethod: (request: ApiRequest, payload: ByteArray) -> Unit

    init {
        logger.logDeveloper(TAG, "Starting DeliveryRetryManager")
        networkConnectivityService.addNetworkConnectivityListener(this)
        lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
        scheduledExecutorService.submit(
            this::scheduleFailedApiCallsRetry
        )
    }

    /**
     * Sets the method to execute to retry requests
     */
    override fun setRetryMethod(retryMethod: (request: ApiRequest, payload: ByteArray) -> Unit) {
        this.retryMethod = retryMethod
    }

    /**
     * Schedules a failed API call for retry.
     */
    override fun scheduleForRetry(request: ApiRequest, payload: ByteArray) {
        logger.logDeveloper(TAG, "Scheduling api call for retry")
        if (pendingRetriesCount() < MAX_FAILED_CALLS || request.isSessionRequest()) {
            val scheduleJob = retryQueue.isEmpty()
            val cachedPayloadName = cacheManager.savePayload(payload)
            val failedApiCall = DeliveryFailedApiCall(request, cachedPayloadName)
            retryQueue.add(failedApiCall)
            cacheManager.saveFailedApiCalls(retryQueue)

            // By default there are no scheduled retry jobs pending. If the retry queue was initially empty, try to schedule a retry.
            if (scheduleJob) {
                scheduleFailedApiCallsRetry(RETRY_PERIOD)
            }
        }
    }

    /**
     * Called when the network status has changed.
     */
    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        lastNetworkStatus = status
        when (status) {
            NetworkStatus.UNKNOWN,
            NetworkStatus.WIFI,
            NetworkStatus.WAN -> {
                scheduleFailedApiCallsRetry()
            }

            NetworkStatus.NOT_REACHABLE -> {
                synchronized(this) {
                    lastRetryTask?.let { task ->
                        if (task.cancel(false)) {
                            logger.logDebug("Failed Calls Retry Action was stopped because there is no connection. ")
                            lastRetryTask = null
                        } else {
                            logger.logError("Failed Calls Retry Action could not be stopped.")
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns true if there is an active pending retry task
     */

    fun isRetryTaskActive(): Boolean =
        lastRetryTask?.let { task ->
            !task.isCancelled && !task.isDone
        } ?: false

    /**
     * Returns the number of failed API calls that will be retried
     */

    fun pendingRetriesCount() = retryQueue.size

    /**
     * Return true if the conditions are met that a retry should be scheduled
     */
    private fun shouldScheduleRetry(): Boolean {
        return !isRetryTaskActive() && retryQueue.isNotEmpty()
    }

    /**
     * Schedules an action to retry failed API calls. If the retry doesn't send all the failed API requests, it will recursively schedule
     * itself with an exponential backoff delay, starting with [RETRY_PERIOD], doubling after that until
     * [MAX_EXPONENTIAL_RETRY_PERIOD] is reached, after which case it stops trying until the next cold start.
     */
    private fun scheduleFailedApiCallsRetry(delayInSeconds: Long = 0L) {
        try {
            synchronized(this) {
                if (shouldScheduleRetry()) {
                    lastRetryTask = scheduledExecutorService.schedule(
                        {
                            var noFailedRetries = true
                            if (lastNetworkStatus != NetworkStatus.NOT_REACHABLE) {
                                try {
                                    logger.logInfo("Retrying failed API calls")
                                    logger.logDeveloper(TAG, "Retrying failed API calls")
                                    val retries = pendingRetriesCount()
                                    repeat(retries) {
                                        retryQueue.poll()?.let { failedApiCall ->
                                            val callSucceeded = retryFailedApiCall(failedApiCall)
                                            if (callSucceeded) {
                                                // if the retry succeeded, save the modified queue in cache.
                                                cacheManager.saveFailedApiCalls(retryQueue)
                                            } else {
                                                // if the retry failed, add the call back to the queue.
                                                retryQueue.add(failedApiCall)
                                                noFailedRetries = false
                                            }
                                        }
                                    }
                                } catch (ex: Exception) {
                                    logger.logDebug("Error when retrying failed API call", ex)
                                }
                                if (retryQueue.isNotEmpty()) {
                                    scheduledExecutorService.submit {
                                        scheduleNextFailedApiCallsRetry(
                                            noFailedRetries,
                                            delayInSeconds
                                        )
                                    }
                                }
                            } else {
                                logger.logInfo(
                                    "Did not retry network calls as scheduled because the network is not reachable"
                                )
                            }
                        },
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
            logger.logError("Cannot schedule retry failed calls.", e)
        }
    }

    /**
     * Executes the network call for a DeliveryFailedApiCall.
     */
    private fun retryFailedApiCall(call: DeliveryFailedApiCall): Boolean {
        val payload = cacheManager.loadPayload(call.cachedPayload)
        if (payload != null) {
            try {
                logger.logDeveloper(TAG, "Retrying failed API call")
                retryMethod(call.apiRequest, payload)
                cacheManager.deletePayload(call.cachedPayload)
            } catch (ex: Exception) {
                logger.logDeveloper(
                    TAG,
                    "retried call but fail again, scheduling to retry later",
                    ex
                )
                return false
            }
        } else {
            logger.logError("Could not retrieve cached api payload")
            // If payload is null, the file could have been removed.
            // We don't need to retry sending in the future as we'd get the same result.
            // That's the reason for returning true.
        }
        return true
    }

    /**
     * Schedules the next call to retry sending the failed_api_calls again. The delay will be extended if the previous retry yielded
     * at least one failed request.
     */
    private fun scheduleNextFailedApiCallsRetry(noFailedRetries: Boolean, delay: Long) {
        val nextDelay = if (noFailedRetries) {
            RETRY_PERIOD
        } else {
            // if a network call failed, the retries will use exponential backoff
            max(RETRY_PERIOD, delay * 2)
        }
        if (nextDelay <= MAX_EXPONENTIAL_RETRY_PERIOD) {
            scheduleFailedApiCallsRetry(nextDelay)
        }
    }
}

private const val TAG = "EmbraceDeliveryRetryManager"
private const val RETRY_PERIOD = 120L // In seconds
private const val MAX_EXPONENTIAL_RETRY_PERIOD = 3600 // In seconds
private const val MAX_FAILED_CALLS = 200 // Max number of failed calls that will be cached for retry
