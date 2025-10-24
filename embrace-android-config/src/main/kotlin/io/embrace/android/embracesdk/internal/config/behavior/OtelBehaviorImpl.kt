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
    private val remote = remote?.otelKotlinSdkConfig

    override fun shouldUseKotlinSdk(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled) ?: local.isOtelKotlinSdkEnabled()
    }
}
