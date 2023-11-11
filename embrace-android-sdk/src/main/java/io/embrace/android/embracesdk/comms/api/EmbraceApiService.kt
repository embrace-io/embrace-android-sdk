package io.embrace.android.embracesdk.comms.api

import com.google.gson.stream.JsonReader
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.DeliveryRetryManager
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.io.StringReader
import java.net.HttpURLConnection
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class EmbraceApiService(
    private val apiClient: ApiClient,
    private val serializer: EmbraceSerializer,
    private val cachedConfigProvider: (url: String, request: ApiRequest) -> CachedConfig,
    private val logger: InternalEmbraceLogger,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val cacheManager: DeliveryCacheManager,
    private val deliveryRetryManager: DeliveryRetryManager,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    urlBuilder: ApiUrlBuilder,
    networkConnectivityService: NetworkConnectivityService
) : ApiService, NetworkConnectivityListener {

    private val configUrl = urlBuilder.getConfigUrl()
    private val apiUrls = Endpoint.values().associateWith { EmbraceUrl.create(urlBuilder.getEmbraceUrlWithSuffix(it.path)) }
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN

    init {
        networkConnectivityService.addNetworkConnectivityListener(this)
        lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
        deliveryRetryManager.setPostExecutor(this::executePost)
    }

    /**
     * Asynchronously gets the app's SDK configuration.
     *
     * These settings define app-specific settings, such as disabled log patterns, whether
     * screenshots are enabled, as well as limits and thresholds.
     *
     * @return a future containing the configuration.
     */
    @Throws(IllegalStateException::class)
    override fun getConfig(): RemoteConfig? {
        var request = prepareConfigRequest(configUrl)
        val cachedResponse = cachedConfigProvider(configUrl, request)

        if (cachedResponse.isValid()) { // only bother if we have a useful response.
            request = request.copy(eTag = cachedResponse.eTag)
        }
        val response = apiClient.executeGet(request)
        return handleRemoteConfigResponse(response, cachedResponse.config)
    }

    override fun getCachedConfig(): CachedConfig {
        val request = prepareConfigRequest(configUrl)
        return cachedConfigProvider(configUrl, request)
    }

    private fun prepareConfigRequest(url: String) = ApiRequest(
        contentType = "application/json",
        userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME,
        accept = "application/json",
        url = EmbraceUrl.create(url),
        httpMethod = HttpMethod.GET,
    )

    private fun handleRemoteConfigResponse(
        response: ApiResponse<String>,
        cachedConfig: RemoteConfig?
    ): RemoteConfig? {
        return when (response.statusCode) {
            HttpURLConnection.HTTP_OK -> {
                logger.logInfo("Fetched new config successfully.")
                val jsonReader = JsonReader(StringReader(response.body))
                serializer.loadObject(jsonReader, RemoteConfig::class.java)
            }

            HttpURLConnection.HTTP_NOT_MODIFIED -> {
                logger.logInfo("Confirmed config has not been modified.")
                cachedConfig
            }

            ApiClient.NO_HTTP_RESPONSE -> {
                logger.logInfo("Failed to fetch config (no response).")
                null
            }

            else -> {
                logger.logWarning("Unexpected status code when fetching config: ${response.statusCode}")
                null
            }
        }
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        lastNetworkStatus = status
    }

    /**
     * Sends a log message to the API.
     *
     * @param eventMessage the event message containing the log entry
     * @return a future containing the response body from the server
     */
    override fun sendLogs(eventMessage: EventMessage) {
        apiUrls[Endpoint.LOGGING]?.let { url ->
            val event = eventMessage.event
            val abbreviation = event.type.abbreviation
            val logIdentifier = abbreviation + ":" + event.messageId
            val request: ApiRequest = eventBuilder(url).copy(logId = logIdentifier)
            postEvent(eventMessage, request)
        }
    }

    /**
     * Sends an Application Exit Info (AEI) blob message to the API.
     *
     * @param blobMessage the blob message containing the AEI data
     * @return a future containing the response body from the server
     */
    override fun sendAEIBlob(blobMessage: BlobMessage) {
        apiUrls[Endpoint.BLOBS]?.let { url ->
            val request: ApiRequest = eventBuilder(url).copy(
                deviceId = lazyDeviceId.value,
                appId = appId,
                url = url,
                httpMethod = HttpMethod.POST,
                contentEncoding = "gzip"
            )

            postAEIBlob(blobMessage, request)
        }
    }

    /**
     * Sends a network event to the API.
     *
     * @param networkEvent the event containing the network call information
     */
    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        apiUrls[Endpoint.NETWORK]?.let { url ->
            val abbreviation = EmbraceEvent.Type.NETWORK_LOG.abbreviation
            val networkIdentifier = "$abbreviation:${networkEvent.eventId}"
            val request: ApiRequest = eventBuilder(url).copy(logId = networkIdentifier)
            postNetworkEvent(networkEvent, request)
        }
    }

    /**
     * Sends an event to the API.
     *
     * @param eventMessage the event message containing the event
     */
    override fun sendEvent(eventMessage: EventMessage) {
        createRequest(eventMessage)?.let { request ->
            postEvent(eventMessage, request)
        }
    }

    /**
     * Sends an event to the API and waits for the request to be completed
     *
     * @param eventMessage the event message containing the event
     */
    override fun sendEventAndWait(eventMessage: EventMessage) {
        createRequest(eventMessage)?.let { request ->
            postEvent(eventMessage, request)?.get()
        }
    }

    /**
     * Sends a crash event to the API and reschedules it if the request times out
     *
     * @param crash the event message containing the crash
     */
    override fun sendCrash(crash: EventMessage) {
        createRequest(crash)?.let { request ->
            try {
                postEvent(crash, request) { cacheManager.deleteCrash() }?.get(
                    CRASH_TIMEOUT,
                    TimeUnit.SECONDS
                )
            } catch (e: Exception) {
                logger.logError("The crash report request has timed out.")
            }
        }
    }

    override fun sendSession(sessionPayload: ByteArray, onFinish: (() -> Unit)?): Future<*>? {
        apiUrls[Endpoint.SESSIONS]?.let { url ->
            val request: ApiRequest = eventBuilder(url).copy(
                deviceId = lazyDeviceId.value,
                appId = appId,
                url = url,
                httpMethod = HttpMethod.POST,
                contentEncoding = "gzip"
            )
            return postOnExecutor(sessionPayload, request, onFinish)
        }

        return null
    }

    private fun createRequest(eventMessage: EventMessage): ApiRequest? {
        apiUrls[Endpoint.EVENTS]?.let { url ->
            val event = eventMessage.event
            val abbreviation = event.type.abbreviation
            val eventIdentifier: String = if (event.type == EmbraceEvent.Type.CRASH) {
                createCrashActiveEventsHeader(abbreviation, event.activeEventIds)
            } else {
                abbreviation + ":" + event.eventId
            }
            return eventBuilder(url).copy(eventId = eventIdentifier)
        }

        return null
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
            return postOnExecutor(it, request, onComplete)
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
            return postOnExecutor(it, request, null)
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
            return postOnExecutor(it, request, null)
        }
        logger.logError("Failed to serialize event")
        return null
    }

    private fun postOnExecutor(
        payload: ByteArray,
        request: ApiRequest,
        onComplete: (() -> Any)?
    ): Future<*> {
        return scheduledExecutorService.submit {
            try {
                if (lastNetworkStatus != NetworkStatus.NOT_REACHABLE) {
                    executePost(request, payload)
                } else {
                    deliveryRetryManager.scheduleForRetry(request, payload)
                    logger.logWarning("No connection available. Request was queued to retry later.")
                }
            } catch (ex: Exception) {
                logger.logWarning("Failed to post Embrace API call. Will retry.", ex)
                deliveryRetryManager.scheduleForRetry(request, payload)
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
            appId = appId,
            deviceId = lazyDeviceId.value,
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

    private fun executePost(request: ApiRequest, payload: ByteArray) {
        apiClient.executePost(request, payload)
    }

    companion object {
        enum class Endpoint(val path: String) {
            EVENTS("events"),
            BLOBS("blobs"),
            LOGGING("logging"),
            NETWORK("network"),
            SESSIONS("sessions")
        }
    }
}

private const val TAG = "EmbraceApiService"
private const val CRASH_TIMEOUT = 1L // Seconds to wait before timing out when sending a crash
