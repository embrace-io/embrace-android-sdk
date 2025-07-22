package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.spans.EmbraceSpan

class FakeNetworkLoggingService : NetworkLoggingService {

    var requests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }

    override fun startNetworkRequestSpan(
        httpMethod: HttpMethod,
        url: String,
        startTimeMs: Long,
    ): EmbraceSpan? {
        return null
    }

    override fun endNetworkRequestSpan(
        networkRequest: EmbraceNetworkRequest,
        span: EmbraceSpan,
    ) {
    }
}
