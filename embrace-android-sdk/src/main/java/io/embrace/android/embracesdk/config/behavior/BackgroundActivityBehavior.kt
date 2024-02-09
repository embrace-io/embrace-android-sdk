package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.BackgroundActivityLocalConfig
import io.embrace.android.embracesdk.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
internal class BackgroundActivityBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<BackgroundActivityLocalConfig?>,
    remoteSupplier: Provider<BackgroundActivityRemoteConfig?>
) : MergedConfigBehavior<BackgroundActivityLocalConfig, BackgroundActivityRemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {
        const val BACKGROUND_ACTIVITY_CAPTURE_ENABLED_DEFAULT = false
        const val MANUAL_BACKGROUND_ACTIVITY_LIMIT_DEFAULT = 100
        const val MIN_BACKGROUND_ACTIVITY_DURATION_DEFAULT = 5000L
        const val MAX_CACHED_ACTIVITIES_DEFAULT = 30
    }

    /**
     * Whether the feature is enabled or not.
     */
    fun isEnabled(): Boolean {
        return remote?.threshold?.let(thresholdCheck::isBehaviorEnabled)
            ?: local?.backgroundActivityCaptureEnabled
            ?: BACKGROUND_ACTIVITY_CAPTURE_ENABLED_DEFAULT
    }

    /**
     * Specify a maximum number of client defined background activities.
     */
    fun getManualBackgroundActivityLimit(): Int {
        return local?.manualBackgroundActivityLimit ?: MANUAL_BACKGROUND_ACTIVITY_LIMIT_DEFAULT
    }

    /**
     * Specify a minimum duration for a client defined background activity.
     */
    fun getMinBackgroundActivityDuration(): Long {
        return local?.minBackgroundActivityDuration ?: MIN_BACKGROUND_ACTIVITY_DURATION_DEFAULT
    }

    /**
     * Specify the max number of background activities cached to disk at the same time.
     */
    fun getMaxCachedActivities(): Int {
        return local?.maxCachedActivities ?: MAX_CACHED_ACTIVITIES_DEFAULT
    }
}
