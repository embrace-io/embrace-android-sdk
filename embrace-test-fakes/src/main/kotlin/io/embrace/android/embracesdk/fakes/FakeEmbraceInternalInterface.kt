package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

class FakeEmbraceInternalInterface(
    var networkSpanForwardingEnabled: Boolean = false,
) : EmbraceInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled

    override fun addEnvelopeResource(key: String, value: String) {
    }

    override fun setHostedSdkVersion(version: String?) {
    }

    override fun setHostedPlatformVersion(version: String?) {
    }
}
