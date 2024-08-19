package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

public class FakeNetworkLoggingService : NetworkLoggingService {

    public var requests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }
}
