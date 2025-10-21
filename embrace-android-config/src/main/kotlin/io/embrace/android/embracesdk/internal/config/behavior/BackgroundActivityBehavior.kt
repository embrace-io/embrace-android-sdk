package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig

interface BackgroundActivityBehavior : ConfigBehavior<EnabledFeatureConfig, BackgroundActivityRemoteConfig> {

    /**
     * Whether the feature is enabled or not.
     */
    fun isBackgroundActivityCaptureEnabled(): Boolean

    /**
     * Specify a maximum number of client defined background activities.
     */
    fun getManualBackgroundActivityLimit(): Int

    /**
     * Specify a minimum duration for a client defined background activity.
     */
    fun getMinBackgroundActivityDuration(): Long

    /**
     * Specify the max number of background activities cached to disk at the same time.
     */
    fun getMaxCachedActivities(): Int
}
