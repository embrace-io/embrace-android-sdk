package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class NetworkSpanForwardingBehaviorImpl(
    private val traceparentInjectionBehavior: TraceparentInjectionBehavior,
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

    private val enabledFeatures: EnabledFeatureConfig = local.enabledFeatures

    private val nsfPctEnabled: Float? = remote?.let {
        @Suppress("DEPRECATION")
        it.nsfPctEnabled ?: it.networkSpanForwardingRemoteConfig?.pctEnabled
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        nsfFeatureFlagEnabled() && traceparentInjectionBehavior.isTraceparentInjectionEnabled()

    override fun shouldForwardForDomain(host: String?): Boolean =
        nsfFeatureFlagEnabled() && traceparentInjectionBehavior.shouldInjectTraceparent(host)

    private fun nsfFeatureFlagEnabled(): Boolean {
        return nsfPctEnabled?.let { thresholdCheck.isBehaviorEnabled(it) }
            ?: enabledFeatures.isNetworkSpanForwardingEnabled()
    }
}
