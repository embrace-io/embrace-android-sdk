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

class RequestExecutionServiceImpl(
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
) : RequestExecutionService {

    private val okHttpClient = OkHttpClient()
        .newBuilder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .build()

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
    ): ApiResponse {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()

        val requestBody = object : RequestBody() {
            override fun contentType() = null

            override fun writeTo(sink: BufferedSink) {
                payloadStream().source().buffer().use { source ->
                    sink.writeAll(source)
                }
            }
        }

        val request = Request.Builder()
            .url(apiRequest.url)
            .headers(apiRequest.getHeaders().toHeaders())
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return ApiResponse.Success(null, null)
            }
            return ApiResponse.Failure(response.code, null)
        }
    }

    private fun getEmbraceUrlWithSuffix(suffix: String): String {
        return "$coreBaseUrl/v2/$suffix"
    }

    private fun Endpoint.getApiRequestFromEndpoint(): ApiRequestV2 = ApiRequestV2(
        url = getEmbraceUrlWithSuffix(this.path),
        appId = appId,
        deviceId = lazyDeviceId.value,
        contentEncoding = "gzip",
        userAgent = "Embrace/a/$embraceVersionName"
    )

    private fun ApiRequestV2.getHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to accept,
            "User-Agent" to userAgent,
            "Content-Type" to contentType
        )
        contentEncoding?.let { headers["Content-Encoding"] = it }
        acceptEncoding?.let { headers["Accept-Encoding"] = it }
        appId?.let { headers["X-EM-AID"] = it }
        deviceId?.let { headers["X-EM-DID"] = it }
        eventId?.let { headers["X-EM-SID"] = it }
        logId?.let { headers["X-EM-LID"] = it }
        eTag?.let { headers["If-None-Match"] = it }
        return headers
    }
}
