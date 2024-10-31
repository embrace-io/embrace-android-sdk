package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
class BackgroundActivityBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<BackgroundActivityRemoteConfig?>,
) : BackgroundActivityBehavior,
    MergedConfigBehavior<UnimplementedConfig, BackgroundActivityRemoteConfig>(
        thresholdCheck = thresholdCheck,
        remoteSupplier = remoteSupplier
    ) {

    private val cfg = InstrumentedConfig.backgroundActivity

    override fun isBackgroundActivityCaptureEnabled(): Boolean {
        return remote?.threshold?.let(thresholdCheck::isBehaviorEnabled)
            ?: InstrumentedConfig.enabledFeatures.isBackgroundActivityCaptureEnabled()
    }

    override fun getManualBackgroundActivityLimit(): Int = cfg.getManualBackgroundActivityLimit()
    override fun getMinBackgroundActivityDuration(): Long = cfg.getMinBackgroundActivityDuration()
    override fun getMaxCachedActivities(): Int = cfg.getMaxCachedActivities()
}
