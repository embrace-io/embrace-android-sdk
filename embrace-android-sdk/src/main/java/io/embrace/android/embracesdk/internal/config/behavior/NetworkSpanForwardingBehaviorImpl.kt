package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UnimplementedConfig

internal class NetworkSpanForwardingBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<NetworkSpanForwardingRemoteConfig?>
) : NetworkSpanForwardingBehavior, MergedConfigBehavior<UnimplementedConfig, NetworkSpanForwardingRemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {
    companion object {
        /**
         * Header name for the W3C traceparent
         */
        const val TRACEPARENT_HEADER_NAME = "traceparent"

        private const val DEFAULT_PCT_ENABLED = 0.0f
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled ?: DEFAULT_PCT_ENABLED)
    }
}
