package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult.Companion.getResult
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10L
private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L

class OkHttpRequestExecutionService(
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
    connectionTimeoutSeconds: Long = DEFAULT_CONNECTION_TIMEOUT_SECONDS,
    readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
) : RequestExecutionService {

    private val okHttpClient = OkHttpClient()
        .newBuilder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(connectionTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
    ): ExecutionResult {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()
        val requestBody = generateRequestBody(payloadStream)
        val request = Request.Builder()
            .url(apiRequest.url)
            .headers(apiRequest.getHeaders().toHeaders())
            .post(requestBody)
            .build()

        var failureReason: Throwable? = null
        val httpCallResponse = try {
            okHttpClient.newCall(request).execute()
        } catch (throwable: Throwable) {
            failureReason = throwable
            null
        }

        return getResult(
            endpoint = envelopeType.endpoint,
            responseCode = httpCallResponse?.code,
            headersProvider = {
                httpCallResponse?.run {
                    httpCallResponse.headers.toMap()
                } ?: emptyMap()
            },
            clientError = failureReason,
        )
    }

    private val mediaType = "application/json".toMediaType()

    private fun generateRequestBody(payloadStream: () -> InputStream) = object : RequestBody() {
        override fun contentType() = mediaType

        override fun writeTo(sink: BufferedSink) {
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
