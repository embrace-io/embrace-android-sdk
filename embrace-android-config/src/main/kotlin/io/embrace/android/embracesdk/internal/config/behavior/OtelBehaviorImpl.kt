package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior for OpenTelemetry configuration
 */
class OtelBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : OtelBehavior {

    private val local = local.enabledFeatures
    private val otelKotlinSdkConfig = remote?.otelKotlinSdkConfig
    private val dataConfig = remote?.dataConfig

    override fun shouldUseKotlinSdk(): Boolean {
        return thresholdCheck.isBehaviorEnabled(otelKotlinSdkConfig?.pctEnabled) ?: local.isOtelKotlinSdkEnabled()
    }

    override fun getMaxCustomSpansPerSessionPart(): Int =
        dataConfig?.maxCustomSpansPerSession.asSpanLimit(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART)

    override fun getMaxInternalSpansPerSessionPart(): Int =
        dataConfig?.maxInternalSpansPerSession.asSpanLimit(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART)

    override fun getMaxNetworkSpansPerSessionPart(): Int =
        dataConfig?.maxNetworkSpansPerSession.asSpanLimit(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART)

    private fun Int?.asSpanLimit(default: Int): Int = this?.coerceAtLeast(0) ?: default
}
