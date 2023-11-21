package io.embrace.android.embracesdk.comms.api

import com.google.gson.stream.JsonReader
import io.embrace.android.embracesdk.BuildConfig
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
    lazyDeviceId: Lazy<String>,
    appId: String,
    urlBuilder: ApiUrlBuilder,
    networkConnectivityService: NetworkConnectivityService
) : ApiService, NetworkConnectivityListener {

    private val mapper = ApiRequestMapper(urlBuilder, lazyDeviceId, appId)
    private val configUrl = urlBuilder.getConfigUrl()
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN

    init {
        networkConnectivityService.addNetworkConnectivityListener(this)
        lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
        deliveryRetryManager.setRetryMethod(this::executePost)
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
    @Suppress("UseCheckOrError")
    override fun getConfig(): RemoteConfig? {
        var request = prepareConfigRequest(configUrl)
        val cachedResponse = cachedConfigProvider(configUrl, request)
        if (cachedResponse.isValid()) { // only bother if we have a useful response.
            request = request.copy(eTag = cachedResponse.eTag)
        }

        return when (val response = apiClient.executeGet(request)) {
            is ApiResponse.Success -> {
                logger.logInfo("Fetched new config successfully.")
                val jsonReader = JsonReader(StringReader(response.body))
                serializer.loadObject(jsonReader, RemoteConfig::class.java)
            }
            is ApiResponse.NotModified -> {
                logger.logInfo("Confirmed config has not been modified.")
                cachedResponse.remoteConfig
            }
            is ApiResponse.TooManyRequests -> {
                // TODO: We should retry after the retryAfter time or 3 seconds and apply exponential backoff.
                logger.logWarning("Too many requests. ")
                null
            }
            is ApiResponse.Failure -> {
                logger.logInfo("Failed to fetch config (no response).")
                null
            }
            is ApiResponse.Incomplete -> {
                logger.logWarning("Failed to fetch config.", response.exception)
                throw response.exception
            }
            ApiResponse.PayloadTooLarge -> {
                // Not expected to receive a 413 response for a GET request.
                null
            }
        }
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

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        lastNetworkStatus = status
    }

    /**
     * Sends a log message to the API.
     *
     * @param eventMessage the event message containing the log entry
     * @return a future containing the response body from the server
     */
    override fun sendLog(eventMessage: EventMessage) {
        post(eventMessage, mapper::logRequest)
    }

    /**
     * Sends an Application Exit Info (AEI) blob message to the API.
     *
     * @param blobMessage the blob message containing the AEI data
     * @return a future containing the response body from the server
     */
    override fun sendAEIBlob(blobMessage: BlobMessage) {
        post(blobMessage, mapper::aeiBlobRequest)
    }

    /**
     * Sends a network event to the API.
     *
     * @param networkEvent the event containing the network call information
     */
    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        post(networkEvent, mapper::networkEventRequest)
    }

    /**
     * Sends an event to the API.
     *
     * @param eventMessage the event message containing the event
     */
    override fun sendEvent(eventMessage: EventMessage) {
        post(eventMessage, mapper::eventMessageRequest)
    }

    /**
     * Sends an event to the API and waits for the request to be completed
     *
     * @param eventMessage the event message containing the event
     */
    override fun sendEventAndWait(eventMessage: EventMessage) {
        post(eventMessage, mapper::eventMessageRequest)?.get()
    }

    /**
     * Sends a crash event to the API and reschedules it if the request times out
     *
     * @param crash the event message containing the crash
     */
    override fun sendCrash(crash: EventMessage) {
        try {
            post(crash, mapper::eventMessageRequest) { cacheManager.deleteCrash() }?.get(
                CRASH_TIMEOUT,
                TimeUnit.SECONDS
            )
        } catch (e: Exception) {
            logger.logError("The crash report request has timed out.")
        }
    }

    override fun sendSession(sessionPayload: ByteArray, onFinish: (() -> Unit)?): Future<*> {
        val request: ApiRequest = mapper.sessionRequest()
        return postOnExecutor(sessionPayload, request, onFinish)
    }

    private inline fun <reified T> post(
        payload: T,
        mapper: (T) -> ApiRequest,
        noinline onComplete: (() -> Unit)? = null
    ): Future<*>? {
        val bytes = serializer.bytesFromPayload(payload, T::class.java)
        val request: ApiRequest = mapper(payload)

        bytes?.let {
            logger.logDeveloper(TAG, "Post event")
            return postOnExecutor(it, request, onComplete)
        }
        logger.logError("Failed to post event")
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

    @Suppress("UseCheckOrError")
    private fun executePost(request: ApiRequest, payload: ByteArray) {
        val response = apiClient.executePost(request, payload)
        if (response !is ApiResponse.Success) {
            throw IllegalStateException("Failed to retrieve from Embrace server.")
        }
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
