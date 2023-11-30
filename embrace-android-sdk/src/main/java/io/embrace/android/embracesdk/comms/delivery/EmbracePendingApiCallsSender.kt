package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.api.ApiRequest
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
    private val clock: Clock
) : PendingApiCallsSender, NetworkConnectivityListener {

    private val pendingApiCalls: PendingApiCalls by lazy {
        cacheManager.loadPendingApiCalls()
    }
    private var lastDeliveryTask: ScheduledFuture<*>? = null
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN
    private lateinit var sendMethod: (request: ApiRequest, payload: ByteArray) -> Unit

    init {
        logger.logDeveloper(TAG, "Starting DeliveryRetryManager")
        networkConnectivityService.addNetworkConnectivityListener(this)
        lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
        scheduledExecutorService.submit(this::scheduleApiCallsDelivery)
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> Unit) {
        this.sendMethod = sendMethod
    }

    override fun scheduleApiCall(request: ApiRequest, payload: ByteArray) {
        logger.logDeveloper(TAG, "Scheduling api call for retry")

        val cachedPayloadName = cacheManager.savePayload(payload)
        val pendingApiCall = PendingApiCall(request, cachedPayloadName, clock.now())

        val scheduleJob = pendingApiCalls.hasAnyPendingApiCall().not()

        pendingApiCalls.add(pendingApiCall)
        cacheManager.savePendingApiCalls(pendingApiCalls)

        // By default there are no scheduled retry jobs pending.
        // If the retry map was initially empty, try to schedule a retry.
        if (scheduleJob) {
            scheduleApiCallsDelivery(RETRY_PERIOD)
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
                scheduleApiCallsDelivery()
            }

            NetworkStatus.NOT_REACHABLE -> {
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
        return !isDeliveryTaskActive() && pendingApiCalls.hasAnyPendingApiCall()
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
                        {
                            if (lastNetworkStatus != NetworkStatus.NOT_REACHABLE) {
                                try {
                                    logger.logDeveloper(TAG, "Sending Pending API calls")

                                    val failedApiCallsToRetry = mutableListOf<PendingApiCall>()

                                    while (true) {
                                        val pendingApiCall = pendingApiCalls.pollNextPendingApiCall()
                                        pendingApiCall?.let {
                                            val callSucceeded = sendPendingApiCall(pendingApiCall)
                                            if (callSucceeded) {
                                                // if the retry succeeded, save the modified queue in cache.
                                                cacheManager.savePendingApiCalls(pendingApiCalls)
                                            } else {
                                                // if the retry failed, it will be added back to the queue.
                                                failedApiCallsToRetry.add(pendingApiCall)
                                            }
                                        } ?: break
                                    }

                                    // Add back to the queue all retries that failed.
                                    failedApiCallsToRetry.forEach {
                                        pendingApiCalls.add(it)
                                    }

                                    if (pendingApiCalls.hasAnyPendingApiCall()) {
                                        scheduledExecutorService.submit {
                                            val allApiCallsSent = failedApiCallsToRetry.isEmpty()
                                            scheduleNextApiCallsDelivery(
                                                allApiCallsSent,
                                                delayInSeconds
                                            )
                                        }
                                    }
                                } catch (ex: Exception) {
                                    logger.logDebug("Error when sending API call", ex)
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
                        "Scheduled API calls to be sent ${if (delayInSeconds == 0L) "now" else "in $delayInSeconds seconds"}"
                    )
                }
            }
        } catch (e: RejectedExecutionException) {
            // This happens if the executor has shutdown previous to the schedule call
            logger.logError("Cannot schedule delivery of pending api calls.", e)
        }
    }

    /**
     * Send the request for a PendingApiCall.
     */
    private fun sendPendingApiCall(call: PendingApiCall): Boolean {
        val payload = cacheManager.loadPayload(call.cachedPayloadFilename)
        if (payload != null) {
            try {
                logger.logDeveloper(TAG, "Sending a Pending API call")
                sendMethod(call.apiRequest, payload)
                cacheManager.deletePayload(call.cachedPayloadFilename)
            } catch (ex: Exception) {
                logger.logDeveloper(
                    TAG,
                    "Sending the Pending Api Call failed, scheduling to retry later",
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
     * Schedules the next call to retry sending the pending api calls again.
     * The delay will be extended if the previous retry yielded at least one failed request.
     */
    private fun scheduleNextApiCallsDelivery(allApiCallsSent: Boolean, delay: Long) {
        val nextDelay = if (allApiCallsSent) {
            RETRY_PERIOD
        } else {
            // if a network call failed, the retries will use exponential backoff
            max(RETRY_PERIOD, delay * 2)
        }
        if (nextDelay <= MAX_EXPONENTIAL_RETRY_PERIOD) {
            scheduleApiCallsDelivery(nextDelay)
        }
    }
}

private const val TAG = "EmbracePendingApiCallsSender"
private const val RETRY_PERIOD = 120L // In seconds
private const val MAX_EXPONENTIAL_RETRY_PERIOD = 3600 // In seconds
