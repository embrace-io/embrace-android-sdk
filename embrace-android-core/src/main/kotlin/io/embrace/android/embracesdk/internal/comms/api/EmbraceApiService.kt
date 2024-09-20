package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PrioritizedWorker
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import io.embrace.android.embracesdk.network.http.HttpMethod
import java.lang.reflect.ParameterizedType
import java.util.concurrent.Future

internal class EmbraceApiService(
    private val apiClient: ApiClient,
    private val serializer: PlatformSerializer,
    private val cachedConfigProvider: (url: String, request: ApiRequest) -> CachedConfig,
    private val logger: EmbLogger,
    private val prioritizedWorker: PrioritizedWorker,
    private val pendingApiCallsSender: PendingApiCallsSender,
    lazyDeviceId: Lazy<String>,
    appId: String,
    urlBuilder: ApiUrlBuilder
) : ApiService {

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
            pendingApiCallsSender.initializeRetrySchedule(this::executePost)
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
                response.body?.let {
                    serializer.fromJson(it, RemoteConfig::class.java)
                }
            }

            is ApiResponse.NotModified -> {
                cachedResponse.remoteConfig
            }

            is ApiResponse.TooManyRequests -> {
                // TODO: We should retry after the retryAfter time or 3 seconds and apply exponential backoff.
                null
            }

            is ApiResponse.Failure, ApiResponse.None -> {
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
        url = ApiRequestUrl(url),
        httpMethod = HttpMethod.GET,
    )

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        lastNetworkStatus = status
    }

    override fun sendLogEnvelope(logEnvelope: Envelope<LogPayload>) {
        val parameterizedType = TypeUtils.parameterizedType(Envelope::class, LogPayload::class)
        post(logEnvelope, mapper::logsEnvelopeRequest, parameterizedType)
    }

    override fun saveLogEnvelope(logEnvelope: Envelope<LogPayload>) {
        val parameterizedType = TypeUtils.parameterizedType(Envelope::class, LogPayload::class)
        val request: ApiRequest = mapper.logsEnvelopeRequest(logEnvelope)
        val action: SerializationAction = { stream ->
            ConditionalGzipOutputStream(stream).use {
                serializer.toJson(logEnvelope, parameterizedType, it)
            }
        }
        pendingApiCallsSender.savePendingApiCall(request, action, sync = true)
    }

    override fun sendEvent(eventMessage: EventMessage) {
        post(eventMessage, mapper::eventMessageRequest)
    }

    override fun sendSession(action: SerializationAction, onFinish: ((response: ApiResponse) -> Unit)): Future<*> {
        return postOnWorker(action, mapper.sessionRequest(), onFinish)
    }

    private inline fun <reified T> post(
        payload: T,
        mapper: (T) -> ApiRequest,
        type: ParameterizedType? = null,
        noinline onComplete: ((response: ApiResponse) -> Unit) = {}
    ): Future<*> {
        val request: ApiRequest = mapper(payload)
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
        onComplete: ((response: ApiResponse) -> Unit),
    ): Future<*> {
        val priority = when (request.isSessionRequest()) {
            true -> TaskPriority.CRITICAL
            else -> TaskPriority.NORMAL
        }
        return prioritizedWorker.submit(priority) {
            var response: ApiResponse = ApiResponse.None
            try {
                response = handleApiRequest(request, action)
            } catch (e: Exception) {
                logger.logWarning("API call failed.", e)
            } finally {
                onComplete(response)
            }
        }
    }

    /**
     * Handles an API request by executing it if the device is online and the endpoint is not rate limited.
     * Otherwise, the API call is saved to be sent later.
     */
    private fun handleApiRequest(request: ApiRequest, action: SerializationAction): ApiResponse {
        val url = EmbraceUrl.create(request.url.url)
        val endpoint = url.endpoint()

        if (lastNetworkStatus.isReachable && !endpoint.limiter.isRateLimited) {
            // Execute the request if the device is online and the endpoint is not rate limited.
            val response = executePost(request, action)

            if (response.shouldRetry) {
                pendingApiCallsSender.savePendingApiCall(request, action)
                pendingApiCallsSender.scheduleRetry(response)
            }
            return response
        } else {
            // Otherwise, save the API call to send it once the rate limit is lifted or the device is online again.
            pendingApiCallsSender.savePendingApiCall(request, action)
        }
        return ApiResponse.None
    }

    /**
     * Executes a POST request by calling [ApiClient.executePost].
     */
    private fun executePost(request: ApiRequest, action: SerializationAction) =
        apiClient.executePost(request, action)
}
