package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService

class FakeNetworkLoggingService : NetworkLoggingService {

    var requests: MutableList<HttpNetworkRequest> = mutableListOf()

    override fun logNetworkRequest(request: HttpNetworkRequest) {
        requests.add(request)
    }
}
