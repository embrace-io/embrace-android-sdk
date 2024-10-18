package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

private const val NO_HTTP_RESPONSE = -1
private const val TOO_MANY_REQUESTS = 429
private const val DEFAULT_TIMEOUT_SECONDS = 10L

class OkHttpRequestExecutionService(
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
    connectionTimeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
) : RequestExecutionService {

    private val okHttpClient = OkHttpClient()
        .newBuilder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .connectTimeout(connectionTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(connectionTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
    ): ApiResponse {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()

        val requestBody = generateRequestBody(payloadStream)

        val request = Request.Builder()
            .url(apiRequest.url)
            .headers(apiRequest.getHeaders().toHeaders())
            .post(requestBody)
            .build()

        val httpCallResponse = try {
            okHttpClient.newCall(request).execute()
        } catch (throwable: Throwable) {
            return ApiResponse.Incomplete(throwable)
        }

        httpCallResponse.use { response ->
            return when (response.code) {
                HttpURLConnection.HTTP_OK -> return ApiResponse.Success(null, null)
                HttpURLConnection.HTTP_NOT_MODIFIED -> ApiResponse.NotModified
                HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> ApiResponse.PayloadTooLarge

                TOO_MANY_REQUESTS -> {
                    val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                    ApiResponse.TooManyRequests(envelopeType.endpoint, retryAfter)
                }

                NO_HTTP_RESPONSE -> ApiResponse.Incomplete(
                    IllegalStateException("Connection failed or unexpected response code")
                )

                else -> ApiResponse.Failure(
                    response.code,
                    response.headers.associate { it.first to it.second }
                )
            }
        }
    }

    private fun generateRequestBody(payloadStream: () -> InputStream) = object : RequestBody() {
        override fun contentType() = null

        override fun writeTo(sink: BufferedSink) {
            // TODO: test throwing an exception here to see if it's caught by okHttpClient
            payloadStream().source().buffer().use { source ->
                sink.writeAll(source)
            }
        }
    }

    private fun Endpoint.getApiRequestFromEndpoint(): ApiRequestV2 = ApiRequestV2(
        url = "$coreBaseUrl/v2/${this.path}",
        appId = appId,
        deviceId = lazyDeviceId.value,
        contentEncoding = "gzip",
        userAgent = "Embrace/a/$embraceVersionName"
    )
}
