package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

private const val DEFAULT_USE_KOTLIN_SDK: Boolean = false

/**
 * Provides the behavior for OpenTelemetry configuration
 */
class OtelBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    override val remote: RemoteConfig?,
) : OtelBehavior {

    override val local: UnimplementedConfig = null

    override fun shouldUseKotlinSdk(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.otelKotlinSdkConfig?.pctEnabled) ?: DEFAULT_USE_KOTLIN_SDK
    }
}
