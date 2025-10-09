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

    override val local = local.enabledFeatures
    override val remote = remote?.otelKotlinSdkConfig

    override fun shouldUseKotlinSdk(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled) ?: local.isOtelKotlinSdkEnabled()
    }
}
