package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

class NetworkSpanForwardingBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<NetworkSpanForwardingRemoteConfig?>,
) : NetworkSpanForwardingBehavior, MergedConfigBehavior<UnimplementedConfig, NetworkSpanForwardingRemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {
    companion object {
        /**
         * Header name for the W3C traceparent
         */
        const val TRACEPARENT_HEADER_NAME: String = "traceparent"
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean {
        return remote?.pctEnabled?.let { thresholdCheck.isBehaviorEnabled(it) }
            ?: InstrumentedConfig.enabledFeatures.isNetworkSpanForwardingEnabled()
    }
}
