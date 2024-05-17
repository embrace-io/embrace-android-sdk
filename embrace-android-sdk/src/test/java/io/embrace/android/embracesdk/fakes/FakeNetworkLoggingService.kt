package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService

internal class FakeNetworkLoggingService : NetworkLoggingService {

    var requests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }
}
