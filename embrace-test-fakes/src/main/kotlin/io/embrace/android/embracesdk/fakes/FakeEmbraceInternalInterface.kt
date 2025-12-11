package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

class FakeEmbraceInternalInterface(
    var networkSpanForwardingEnabled: Boolean = false,
    var captureNetworkBody: Boolean = false,
) : EmbraceInternalInterface {

    var networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()
    val internalErrors: MutableList<Throwable> = mutableListOf()

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = captureNetworkBody

    override fun logInternalError(error: Throwable) {
        internalErrors.add(error)
    }
    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        networkRequests.add(networkRequest)
    }
}
