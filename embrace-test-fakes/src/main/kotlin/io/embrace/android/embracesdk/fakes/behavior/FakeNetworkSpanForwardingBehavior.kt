package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior

class FakeNetworkSpanForwardingBehavior(
    private val networkSpanForwardingEnabled: Boolean = false,
) : NetworkSpanForwardingBehavior {

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled
}
