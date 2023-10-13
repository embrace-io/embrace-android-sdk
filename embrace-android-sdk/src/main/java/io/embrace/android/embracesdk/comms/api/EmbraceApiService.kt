package io.embrace.android.embracesdk.comms.api

import com.google.gson.stream.JsonReader
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import java.io.StringReader
import java.net.HttpURLConnection

internal class EmbraceApiService(
    private val apiClientProvider: () -> ApiClient,
    private val urlBuilder: ApiUrlBuilder,
    private val serializer: EmbraceSerializer,
    private val cachedConfigProvider: (url: String, request: ApiRequest) -> CachedConfig,
    private val logger: InternalEmbraceLogger,
) : ApiService {

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
        val url = urlBuilder.getConfigUrl()
        var request = prepareConfigRequest(url)
        val cachedResponse = cachedConfigProvider(url, request)

        if (cachedResponse.isValid()) { // only bother if we have a useful response.
            request = request.copy(eTag = cachedResponse.eTag)
        }
        val apiClient = apiClientProvider.invoke()
        val response = apiClient.executeGet(request)
        return handleRemoteConfigResponse(response, cachedResponse.config)
    }

    override fun getCachedConfig(): CachedConfig {
        val url = urlBuilder.getConfigUrl()
        val request = prepareConfigRequest(url)
        return cachedConfigProvider(url, request)
    }

    private fun prepareConfigRequest(url: String) = ApiRequest(
        contentType = "application/json",
        userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME,
        accept = "application/json",
        url = EmbraceUrl.getUrl(url),
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
}
