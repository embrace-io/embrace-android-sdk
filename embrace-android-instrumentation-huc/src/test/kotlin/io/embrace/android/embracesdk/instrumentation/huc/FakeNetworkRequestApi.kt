package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier

class FakeNetworkRequestApi(
    private val traceparent: String? = null,
) : NetworkRequestApi {
    val requests = mutableListOf<EmbraceNetworkRequest>()
    val httpRequestInfoModifiers = mutableListOf<HttpRequestInfoModifier>()

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        httpRequestInfoModifiers.add(modifier)
    }

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        httpRequestInfoModifiers.remove(modifier)
    }

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = traceparent
}
