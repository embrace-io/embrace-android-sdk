package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.BlobSession
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.utils.exceptions.Unchecked
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val TAG = "DeliveryNetworkManager"
private const val CRASH_TIMEOUT = 1L // Seconds to wait before timing out when sending a crash
private const val RETRY_PERIOD = 120L // In seconds
private const val MAX_EXPONENTIAL_RETRY_PERIOD = 3600 // In seconds
private const val MAX_FAILED_CALLS = 200 // Max number of failed calls that will be cached for retry

internal class DeliveryNetworkManager(
    private val metadataService: MetadataService,
    private val urlBuilder: ApiUrlBuilder,
    private val apiClient: ApiClient,
    private val cacheManager: DeliveryCacheManager,
    private val logger: InternalEmbraceLogger,
    private val scheduledExecutorService: ScheduledExecutorService,
    networkConnectivityService: NetworkConnectivityService,
    private val serializer: EmbraceSerializer,
    private val userService: UserService
) : DeliveryServiceNetwork, NetworkConnectivityListener {

    private val retryQueue: DeliveryFailedApiCalls by lazy { cacheManager.loadFailedApiCalls() }

    private var lastRetryTask: ScheduledFuture<*>? = null

    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN

    init {
        logger.logDeveloper(TAG, "start")
        networkConnectivityService.addNetworkConnectivityListener(this)
        lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
        scheduledExecutorService.submit(
            this::scheduleFailedApiCallsRetry
        )
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        lastNetworkStatus = status
        logger.logDebug("Network status is now: $lastNetworkStatus")
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
     * Sends a log message to the API.
     *
     * @param eventMessage the event message containing the log entry
     * @return a future containing the response body from the server
     */
    override fun sendLogs(eventMessage: EventMessage) {
        logger.logDeveloper(TAG, "sendLogs")
        checkNotNull(eventMessage.event) { "event must be set" }
        val event = eventMessage.event
        checkNotNull(event.type) { "event type must be set" }
        checkNotNull(event.eventId) { "event ID must be set" }
        val url = Unchecked.wrap {
            EmbraceUrl.getUrl(
                urlBuilder.getEmbraceUrlWithSuffix("logging")
            )
        }
        val abbreviation = event.type.abbreviation
        val logIdentifier = abbreviation + ":" + event.messageId
        val request: ApiRequest = eventBuilder(url).copy(logId = logIdentifier)
        postEvent(eventMessage, request)
    }

    /**
     * Sends an Application Exit Info (AEI) blob message to the API.
     *
     * @param appExitInfoData the Application exit info list to send
     * @return a future containing the response body from the server
     */
    override fun sendAEIBlob(appExitInfoData: List<AppExitInfoData>) {
        logger.logDeveloper(TAG, "send BlobMessage")
        val url = Unchecked.wrap {
            EmbraceUrl.getUrl(
                urlBuilder.getEmbraceUrlWithSuffix("blobs")
            )
        }
        val request: ApiRequest = eventBuilder(url).copy(
            deviceId = metadataService.getDeviceId(),
            appId = metadataService.getAppId(),
            url = url,
            httpMethod = HttpMethod.POST,
            contentEncoding = "gzip"
        )

        val blob = BlobMessage(
            metadataService.getAppInfo(),
            appExitInfoData,
            metadataService.getDeviceInfo(),
            BlobSession(metadataService.activeSessionId),
            userService.getUserInfo()
        )

        postAEIBlob(blob, request)
    }

    /**
     * Sends a network event to the API.
     *
     * @param networkEvent the event containing the network call information
     */
    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        logger.logDeveloper(TAG, "sendNetworkCall")

        val url = Unchecked.wrap {
            EmbraceUrl.getUrl(
                urlBuilder.getEmbraceUrlWithSuffix("network")
            )
        }
        val abbreviation = EmbraceEvent.Type.NETWORK_LOG.abbreviation
        val networkIdentifier = "$abbreviation:${networkEvent.eventId}"

        logger.logDeveloper(TAG, "network call to: $url - abbreviation: $abbreviation")

        val request: ApiRequest = eventBuilder(url).copy(logId = networkIdentifier)
        postNetworkEvent(networkEvent, request)
    }

    /**
     * Sends an event to the API.
     *
     * @param eventMessage the event message containing the event
     */
    override fun sendEvent(eventMessage: EventMessage) {
        postEvent(eventMessage, createRequest(eventMessage))
    }

    /**
     * Sends an event to the API and waits for the request to be completed
     *
     * @param eventMessage the event message containing the event
     */
    override fun sendEventAndWait(eventMessage: EventMessage) {
        postEvent(eventMessage, createRequest(eventMessage))?.get()
    }

    /**
     * Sends a crash event to the API and reschedules it if the request times out
     *
     * @param crash the event message containing the crash
     */
    override fun sendCrash(crash: EventMessage) {
        val request = createRequest(crash)
        try {
            postEvent(crash, request) { cacheManager.deleteCrash() }?.get(
                CRASH_TIMEOUT,
                TimeUnit.SECONDS
            )
        } catch (e: Exception) {
            logger.logError("The crash report request has timed out.")
        }
    }

    fun sendSession(sessionPayload: ByteArray, onComplete: (() -> Unit)?): Future<*> {
        logger.logDeveloper(TAG, "sendSession")
        val url = Unchecked.wrap {
            EmbraceUrl.getUrl(
                urlBuilder.getEmbraceUrlWithSuffix("sessions")
            )
        }
        val request: ApiRequest = eventBuilder(url).copy(
            deviceId = metadataService.getDeviceId(),
            appId = metadataService.getAppId(),
            url = url,
            httpMethod = HttpMethod.POST,
            contentEncoding = "gzip"
        )

        return postOnExecutor(sessionPayload, request, true, onComplete)
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

    private fun createRequest(eventMessage: EventMessage): ApiRequest {
        logger.logDeveloper(TAG, "sendEvent")
        checkNotNull(eventMessage.event) { "event must be set" }
        val event = eventMessage.event
        logger.logDeveloper(TAG, "sendEvent - event: " + event.name)
        logger.logDeveloper(TAG, "sendEvent - event: " + event.type)
        checkNotNull(event.type) { "event type must be set" }
        checkNotNull(event.eventId) { "event ID must be set" }
        val url = Unchecked.wrap {
            EmbraceUrl.getUrl(
                urlBuilder.getEmbraceUrlWithSuffix("events")
            )
        }
        val abbreviation = event.type.abbreviation
        val eventIdentifier: String = if (event.type == EmbraceEvent.Type.CRASH) {
            createCrashActiveEventsHeader(abbreviation, event.activeEventIds)
        } else {
            abbreviation + ":" + event.eventId
        }
        return eventBuilder(url).copy(eventId = eventIdentifier)
    }

    private fun postEvent(eventMessage: EventMessage, request: ApiRequest): Future<*>? {
        return postEvent(eventMessage, request, null)
    }

    private fun postEvent(
        eventMessage: EventMessage,
        request: ApiRequest,
        onComplete: (() -> Unit)?
    ): Future<*>? {
        val bytes = serializer.bytesFromPayload(eventMessage, EventMessage::class.java)

        bytes?.let {
            logger.logDeveloper(TAG, "Post event")
            return postOnExecutor(it, request, true, onComplete)
        }
        logger.logError("Failed to serialize event")
        return null
    }

    private fun postNetworkEvent(
        event: NetworkEvent,
        request: ApiRequest
    ): Future<*>? {
        val bytes = serializer.bytesFromPayload(event, NetworkEvent::class.java)

        bytes?.let {
            logger.logDeveloper(TAG, "Post Network Event")
            return postOnExecutor(it, request, true, null)
        }
        logger.logError("Failed to serialize event")
        return null
    }

    private fun postAEIBlob(
        blob: BlobMessage,
        request: ApiRequest
    ): Future<*>? {
        val bytes = serializer.bytesFromPayload(blob, BlobMessage::class.java)

        bytes?.let {
            logger.logDeveloper(TAG, "Post AEI Blob message")
            return postOnExecutor(it, request, true, null)
        }
        logger.logError("Failed to serialize event")
        return null
    }

    private fun postOnExecutor(
        payload: ByteArray,
        request: ApiRequest,
        compress: Boolean,
        onComplete: (() -> Any)?
    ): Future<*> {
        return scheduledExecutorService.submit {
            try {
                if (lastNetworkStatus != NetworkStatus.NOT_REACHABLE) {
                    if (compress) {
                        apiClient.post(request, payload)
                    } else {
                        apiClient.rawPost(request, payload)
                    }
                } else {
                    scheduleForRetry(request, payload)
                    logger.logWarning("No connection available. Request was queued to retry later.")
                }
            } catch (ex: Exception) {
                logger.logWarning("Failed to post Embrace API call. Will retry.", ex)
                scheduleForRetry(request, payload)
                throw ex
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun eventBuilder(url: EmbraceUrl): ApiRequest {
        logger.logDeveloper(TAG, "eventBuilder")
        return ApiRequest(
            url = url,
            httpMethod = HttpMethod.POST,
            appId = metadataService.getAppId(),
            deviceId = metadataService.getDeviceId(),
            contentEncoding = "gzip"
        )
    }

    /**
     * Crashes are sent with a header containing the list of active stories.
     *
     * @param abbreviation the abbreviation for the event type
     * @param eventIds     the list of story IDs
     * @return the header
     */
    private fun createCrashActiveEventsHeader(
        abbreviation: String,
        eventIds: List<String>?
    ): String {
        logger.logDeveloper(TAG, "createCrashActiveEventsHeader")
        val stories = eventIds?.joinToString(",") ?: ""
        return "$abbreviation:$stories"
    }

    private fun scheduleForRetry(request: ApiRequest, payload: ByteArray) {
        logger.logDeveloper(TAG, "Scheduling api call for retry")
        if (pendingRetriesCount() < MAX_FAILED_CALLS) {
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
                apiClient.post(call.apiRequest, payload)
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
