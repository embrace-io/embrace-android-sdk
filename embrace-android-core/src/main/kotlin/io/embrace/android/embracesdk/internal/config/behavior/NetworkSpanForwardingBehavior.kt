package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig

interface NetworkSpanForwardingBehavior : ConfigBehavior<EnabledFeatureConfig, NetworkSpanForwardingRemoteConfig> {
    fun isNetworkSpanForwardingEnabled(): Boolean
}
