package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.BackgroundActivityLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
public class BackgroundActivityBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<BackgroundActivityLocalConfig?>,
    remoteSupplier: Provider<BackgroundActivityRemoteConfig?>
) : BackgroundActivityBehavior, MergedConfigBehavior<BackgroundActivityLocalConfig, BackgroundActivityRemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    private companion object {
        const val BACKGROUND_ACTIVITY_CAPTURE_ENABLED_DEFAULT = false
        const val MANUAL_BACKGROUND_ACTIVITY_LIMIT_DEFAULT = 100
        const val MIN_BACKGROUND_ACTIVITY_DURATION_DEFAULT = 5000L
        const val MAX_CACHED_ACTIVITIES_DEFAULT = 30
    }

    override fun isEnabled(): Boolean {
        return remote?.threshold?.let(thresholdCheck::isBehaviorEnabled)
            ?: local?.backgroundActivityCaptureEnabled
            ?: BACKGROUND_ACTIVITY_CAPTURE_ENABLED_DEFAULT
    }

    override fun getManualBackgroundActivityLimit(): Int {
        return local?.manualBackgroundActivityLimit ?: MANUAL_BACKGROUND_ACTIVITY_LIMIT_DEFAULT
    }

    override fun getMinBackgroundActivityDuration(): Long {
        return local?.minBackgroundActivityDuration ?: MIN_BACKGROUND_ACTIVITY_DURATION_DEFAULT
    }

    override fun getMaxCachedActivities(): Int {
        return local?.maxCachedActivities ?: MAX_CACHED_ACTIVITIES_DEFAULT
    }
}
