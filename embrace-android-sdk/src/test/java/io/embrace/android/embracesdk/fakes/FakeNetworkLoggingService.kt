package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.payload.NetworkSessionV2

internal class FakeNetworkLoggingService : NetworkLoggingService {

    var data: NetworkSessionV2 = NetworkSessionV2(emptyList(), emptyMap())

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        TODO("Not yet implemented")
    }

    override fun logURLConnectionNetworkRequest(callId: String, request: EmbraceNetworkRequest) {
        TODO("Not yet implemented")
    }
}
