package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
class BackgroundActivityBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : BackgroundActivityBehavior {

    override val local: EnabledFeatureConfig = local.enabledFeatures
    override val remote: BackgroundActivityRemoteConfig? = remote?.backgroundActivityConfig

    override fun isBackgroundActivityCaptureEnabled(): Boolean {
        return remote?.threshold?.let(thresholdCheck::isBehaviorEnabled)
            ?: local.isBackgroundActivityCaptureEnabled()
    }

    override fun getManualBackgroundActivityLimit(): Int = 100
    override fun getMinBackgroundActivityDuration(): Long = 5000L
    override fun getMaxCachedActivities(): Int = 30
}
