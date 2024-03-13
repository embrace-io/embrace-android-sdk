package io.embrace.android.embracesdk.comms.api

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.lang.reflect.ParameterizedType
import java.util.concurrent.Future

internal class EmbraceApiService(
    private val apiClient: ApiClient,
    private val serializer: EmbraceSerializer,
    private val cachedConfigProvider: (url: String, request: ApiRequest) -> CachedConfig,
    private val logger: InternalEmbraceLogger,
    private val backgroundWorker: BackgroundWorker,
    private val cacheManager: DeliveryCacheManager,
    private val pendingApiCallsSender: PendingApiCallsSender,
    lazyDeviceId: Lazy<String>,
    appId: String,
    urlBuilder: ApiUrlBuilder,
    networkConnectivityService: NetworkConnectivityService,
) : ApiService, NetworkConnectivityListener {

    private val mapper by lazy {
        Systrace.traceSynchronous("api-request-mapper-init") {
            ApiRequestMapper(urlBuilder, lazyDeviceId, appId)
        }
    }
    private val configUrl by lazy {
        Systrace.traceSynchronous("config-url-init") {
            urlBuilder.getConfigUrl()
        }
    }
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN

    init {
        Systrace.traceSynchronous("api-service-init-block") {
            networkConnectivityService.addNetworkConnectivityListener(this)
            lastNetworkStatus = networkConnectivityService.getCurrentNetworkStatus()
            pendingApiCallsSender.setSendMethod(this::executePost)
        }
    }

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
                response.body?.let {
                    serializer.fromJson(it, RemoteConfig::class.java)
                }
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

    override fun sendLog(eventMessage: EventMessage) {
        post(eventMessage, mapper::logRequest)
    }

    override fun sendLogsEnvelope(logsEnvelope: Envelope<LogPayload>) {
        val parameterizedType = Types.newParameterizedType(Envelope::class.java, LogPayload::class.java)
        post(logsEnvelope, mapper::logsEnvelopeRequest, parameterizedType)
    }

    override fun sendSessionEnvelope(sessionEnvelope: Envelope<SessionPayload>) {
        val parameterizedType = Types.newParameterizedType(Envelope::class.java, SessionPayload::class.java)
        post(sessionEnvelope, mapper::sessionEnvelopeRequest, parameterizedType)
    }

    override fun sendAEIBlob(blobMessage: BlobMessage) {
        post(blobMessage, mapper::aeiBlobRequest)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        post(networkEvent, mapper::networkEventRequest)
    }

    override fun sendEvent(eventMessage: EventMessage) {
        post(eventMessage, mapper::eventMessageRequest)
    }

    override fun sendCrash(crash: EventMessage): Future<*> {
        return post(crash, mapper::eventMessageRequest) { cacheManager.deleteCrash() }
    }

    override fun sendSession(action: SerializationAction, onFinish: (() -> Unit)?): Future<*> {
        return postOnWorker(action, mapper.sessionRequest(), onFinish)
    }

    private inline fun <reified T> post(
        payload: T,
        mapper: (T) -> ApiRequest,
        type: ParameterizedType? = null,
        noinline onComplete: (() -> Unit)? = null,
    ): Future<*> {
        val request: ApiRequest = mapper(payload)
        logger.logDeveloper(TAG, "Post event")

        val action: SerializationAction = { stream ->
            ConditionalGzipOutputStream(stream).use {
                if (type != null) {
                    serializer.toJson(payload, type, it)
                } else {
                    serializer.toJson(payload, T::class.java, it)
                }
            }
        }

        return postOnWorker(action, request, onComplete)
    }

    /**
     * Submits a [NetworkRequestRunnable] to the [backgroundWorker].
     * This way, we prioritize the sending of sessions over other network requests.
     */
    private fun postOnWorker(
        action: SerializationAction,
        request: ApiRequest,
        onComplete: (() -> Any)?,
    ): Future<*> {
        val priority = when (request.isSessionRequest()) {
            true -> TaskPriority.CRITICAL
            else -> TaskPriority.NORMAL
        }
        return backgroundWorker.submit(priority) {
            try {
                handleApiRequest(request, action)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Handles an API request by executing it if the device is online and the endpoint is not rate limited.
     * Otherwise, the API call is saved to be sent later.
     */
    private fun handleApiRequest(request: ApiRequest, action: SerializationAction) {
        val endpoint = request.url.endpoint()

        if (lastNetworkStatus.isReachable && !endpoint.isRateLimited) {
            // Execute the request if the device is online and the endpoint is not rate limited.
            val response = executePost(request, action)

            if (response.shouldRetry) {
                pendingApiCallsSender.savePendingApiCall(request, action)
                pendingApiCallsSender.scheduleRetry(response)
            }

            if (response !is ApiResponse.Success) {
                // If the API call failed, propagate the error to the caller.
                error("Failed to post Embrace API call. ")
            }
        } else {
            // Otherwise, save the API call to send it once the rate limit is lifted or the device is online again.
            pendingApiCallsSender.savePendingApiCall(request, action)
        }
    }

    /**
     * Executes a POST request by calling [ApiClient.executePost].
     */
    private fun executePost(request: ApiRequest, action: SerializationAction) =
        apiClient.executePost(request, action)
}

private const val TAG = "EmbraceApiService"
