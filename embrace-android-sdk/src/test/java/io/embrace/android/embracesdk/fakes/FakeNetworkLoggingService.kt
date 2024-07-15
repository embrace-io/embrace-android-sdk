package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class FakeNetworkLoggingService : NetworkLoggingService {

    var requests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }
}
