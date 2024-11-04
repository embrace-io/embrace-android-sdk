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

    override fun isBackgroundActivityCaptureEnabled(): Boolean {
        return remote?.threshold?.let(thresholdCheck::isBehaviorEnabled)
            ?: InstrumentedConfig.enabledFeatures.isBackgroundActivityCaptureEnabled()
    }

    override fun getManualBackgroundActivityLimit(): Int = 100
    override fun getMinBackgroundActivityDuration(): Long = 5000L
    override fun getMaxCachedActivities(): Int = 30
}
