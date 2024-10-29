package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult.Companion.getResult
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val DEFAULT_TIMEOUT_MILLISECONDS = 10 * 1000

class HttpUrlConnectionRequestExecutionService(
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
    private val logger: EmbLogger,
    private val connectionTimeoutMilliseconds: Int = DEFAULT_TIMEOUT_MILLISECONDS,
) : RequestExecutionService {

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
        payloadType: String,
    ): ExecutionResult {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()
        var headersProvider: (() -> Map<String, String>)? = null
        var executionError: Throwable? = null
        val responseCode = try {
            createUrlConnection(apiRequest, payloadType).run {
                outputStream?.use { outputStream ->
                    payloadStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                connect()
                headersProvider = { readHttpResponseHeaders(this) }
                responseCode
            }
        } catch (throwable: Throwable) {
            // IOExceptions are expected during the execution of a network request is expected, so don't log errors
            // for those. But any unexpected error should be logged.
            if (throwable !is IOException) {
                logger.trackInternalError(
                    type = InternalErrorType.PAYLOAD_DELIVERY_FAIL,
                    throwable = throwable
                )
            }
            executionError = throwable
            null
        }

        return getResult(
            endpoint = envelopeType.endpoint,
            responseCode = responseCode,
            headersProvider = headersProvider ?: { emptyMap() },
            executionError = executionError,
        )
    }

    private fun readHttpResponseHeaders(httpUrlConnection: HttpURLConnection): Map<String, String> = try {
        httpUrlConnection.headerFields?.mapValues { it.value.joinToString() } ?: emptyMap()
    } catch (throwable: Throwable) {
        emptyMap()
    }

    private fun createUrlConnection(apiRequest: ApiRequestV2, payloadType: String): HttpURLConnection {
        val connection = URL(apiRequest.url).openConnection() as HttpURLConnection

        apiRequest.getHeaders().forEach {
            connection.setRequestProperty(it.key, it.value)
        }
        connection.setRequestProperty("X-EM-TYPES", payloadType)

        connection.requestMethod = "POST"
        connection.setDoOutput(true)

        connection.connectTimeout = connectionTimeoutMilliseconds
        connection.readTimeout = connectionTimeoutMilliseconds

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
