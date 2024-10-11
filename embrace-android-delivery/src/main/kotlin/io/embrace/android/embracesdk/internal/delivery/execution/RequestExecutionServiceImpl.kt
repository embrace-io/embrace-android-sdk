package io.embrace.android.embracesdk.internal.delivery.execution

import android.util.Log
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

        val payload: ByteArray = payloadStream().use {
            it.readBytes()
        }

        val request = Request.Builder()
            .url(apiRequest.url)
            .headers(apiRequest.getHeaders().toHeaders())
            .post(payload.toRequestBody())
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            Log.i("FRANEMBRACE", "response protocol: ${response.protocol}")
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
}
