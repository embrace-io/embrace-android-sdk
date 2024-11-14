package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig

class FakeNetworkSpanForwardingBehavior(
    private val networkSpanForwardingEnabled: Boolean = false,
) : NetworkSpanForwardingBehavior {

    override val local: EnabledFeatureConfig
        get() = throw UnsupportedOperationException()
    override val remote: NetworkSpanForwardingRemoteConfig
        get() = throw UnsupportedOperationException()

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled
}
