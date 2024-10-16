package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val NO_HTTP_RESPONSE = -1
private const val TOO_MANY_REQUESTS = 429
private const val DEFAULT_TIMEOUT_SECONDS = 60

class HttpUrlConnectionRequestExecutionService(
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
) : RequestExecutionService {
    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
    ): ApiResponse {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()

        try {
            val httpUrlConnection = createUrlConnection(apiRequest)

            httpUrlConnection.outputStream?.use { outputStream ->
                payloadStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            httpUrlConnection.connect()

            return handleHttpUrlConnectionResponse(httpUrlConnection, envelopeType.endpoint)
        } catch (throwable: Throwable) {
            return ApiResponse.Incomplete(throwable)
        }
    }

    private fun handleHttpUrlConnectionResponse(httpUrlConnection: HttpURLConnection, endpoint: Endpoint): ApiResponse {
        val responseCode = readResponseCode(httpUrlConnection)
        val responseHeaders = readHttpResponseHeaders(httpUrlConnection)

        return when (responseCode) {
            HttpURLConnection.HTTP_OK -> ApiResponse.Success(null, null)
            HttpURLConnection.HTTP_NOT_MODIFIED -> ApiResponse.NotModified
            HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> ApiResponse.PayloadTooLarge
            TOO_MANY_REQUESTS -> ApiResponse.TooManyRequests(endpoint, responseHeaders["Retry-After"]?.toLongOrNull())

            NO_HTTP_RESPONSE -> {
                ApiResponse.Incomplete(IllegalStateException("Connection failed or unexpected response code"))
            }

            else -> ApiResponse.Failure(responseCode, responseHeaders)
        }
    }

    private fun readResponseCode(httpUrlConnection: HttpURLConnection): Int = try {
        httpUrlConnection.responseCode
    } catch (throwable: Throwable) {
        NO_HTTP_RESPONSE
    }

    private fun readHttpResponseHeaders(httpUrlConnection: HttpURLConnection): Map<String, String> {
        return httpUrlConnection.headerFields?.mapValues { it.value.joinToString() } ?: emptyMap()
    }

    private fun createUrlConnection(apiRequest: ApiRequestV2): HttpURLConnection {
        val connection = URL(apiRequest.url).openConnection() as HttpURLConnection

        apiRequest.getHeaders().forEach {
            connection.setRequestProperty(it.key, it.value)
        }
        connection.requestMethod = "POST"
        connection.setDoOutput(true)

        connection.connectTimeout = DEFAULT_TIMEOUT_SECONDS
        connection.readTimeout = DEFAULT_TIMEOUT_SECONDS

        return connection
    }

    private fun Endpoint.getApiRequestFromEndpoint(): ApiRequestV2 = ApiRequestV2(
        url = "$coreBaseUrl/v2/${this.path}",
        appId = appId,
        deviceId = lazyDeviceId.value,
        contentEncoding = "gzip",
        userAgent = "Embrace/a/$embraceVersionName"
    )
}
