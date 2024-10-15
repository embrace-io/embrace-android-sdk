package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.InputStream

class EmbraceOkHttpClient: EmbraceHttpClient {

    private val okHttpClient = OkHttpClient()
        .newBuilder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .build()

    override fun executeRequest(apiRequest: ApiRequestV2, payloadStream: () -> InputStream): ApiResponse {
        val requestBody = generateRequestBody(payloadStream)

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

    private fun generateRequestBody(payloadStream: () -> InputStream) = object : RequestBody() {
        override fun contentType() = null

        override fun writeTo(sink: BufferedSink) {
            payloadStream().source().buffer().use { source ->
                sink.writeAll(source)
            }
        }
    }
}
