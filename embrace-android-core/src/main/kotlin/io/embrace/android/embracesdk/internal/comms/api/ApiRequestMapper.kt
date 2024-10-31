package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.network.http.HttpMethod

internal class ApiRequestMapper(
    private val urlBuilder: ApiUrlBuilder,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
) {

    private val apiUrlBuilders = Endpoint.values().associateWith {
        urlBuilder.getEmbraceUrlWithSuffix(it.version, it.path)
    }

    private fun Endpoint.asEmbraceUrl(): String = checkNotNull(apiUrlBuilders[this])

    private fun requestBuilder(url: String): ApiRequest {
        return ApiRequest(
            url = ApiRequestUrl(url),
            httpMethod = HttpMethod.POST,
            appId = appId,
            deviceId = lazyDeviceId.value,
            contentEncoding = "gzip",
            userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME
        )
    }

    fun configRequest(url: String): ApiRequest = ApiRequest(
        contentType = "application/json",
        userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME,
        accept = "application/json",
        url = ApiRequestUrl(url),
        httpMethod = HttpMethod.GET,
    )

    @Suppress("UNUSED_PARAMETER")
    fun logsEnvelopeRequest(envelope: Envelope<LogPayload>): ApiRequest {
        val url = Endpoint.LOGS.asEmbraceUrl()
        return requestBuilder(url)
    }

    @Suppress("UNUSED_PARAMETER")
    fun sessionEnvelopeRequest(envelope: Envelope<SessionPayload>): ApiRequest {
        val url = Endpoint.SESSIONS.asEmbraceUrl()
        return requestBuilder(url)
    }

    fun sessionRequest(): ApiRequest {
        val url = Endpoint.SESSIONS
        return requestBuilder(url.asEmbraceUrl())
    }
}
