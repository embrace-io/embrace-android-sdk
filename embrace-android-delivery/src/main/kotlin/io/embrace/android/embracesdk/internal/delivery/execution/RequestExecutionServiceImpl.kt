package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import java.io.InputStream

class RequestExecutionServiceImpl(
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
    private val httpClient: EmbraceHttpClient
) : RequestExecutionService {

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
    ): ApiResponse {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()

        return httpClient.executeRequest(apiRequest, payloadStream)
    }

    private fun Endpoint.getApiRequestFromEndpoint(): ApiRequestV2 = ApiRequestV2(
        url = "$coreBaseUrl/v2/${this.path}",
        appId = appId,
        deviceId = lazyDeviceId.value,
        contentEncoding = "gzip",
        userAgent = "Embrace/a/$embraceVersionName"
    )
}
