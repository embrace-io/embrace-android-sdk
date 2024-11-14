package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class NetworkSpanForwardingBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : NetworkSpanForwardingBehavior {

    companion object {
        /**
         * Header name for the W3C traceparent
         */
        const val TRACEPARENT_HEADER_NAME: String = "traceparent"
    }

    override val local: EnabledFeatureConfig = local.enabledFeatures
    override val remote: NetworkSpanForwardingRemoteConfig? = remote?.networkSpanForwardingRemoteConfig

    override fun isNetworkSpanForwardingEnabled(): Boolean {
        return remote?.pctEnabled?.let { thresholdCheck.isBehaviorEnabled(it) }
            ?: local.isNetworkSpanForwardingEnabled()
    }
}
