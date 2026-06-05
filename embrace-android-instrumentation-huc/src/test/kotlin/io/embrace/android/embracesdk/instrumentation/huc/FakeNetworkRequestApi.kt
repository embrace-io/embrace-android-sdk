package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

class FakeNetworkRequestApi(
    private val traceparent: String? = null,
) : NetworkRequestApi {
    val requests = mutableListOf<EmbraceNetworkRequest>()

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = traceparent
}
