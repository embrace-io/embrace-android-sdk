package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

class FakeEmbraceInternalInterface(
    var networkSpanForwardingEnabled: Boolean = false,
) : EmbraceInternalInterface {

    var networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled

    override fun addEnvelopeResource(key: String, value: String) {
    }
}
