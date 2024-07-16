package io.embrace.android.embracesdk.internal.config.behavior

public interface BackgroundActivityBehavior {

    /**
     * Whether the feature is enabled or not.
     */
    public fun isEnabled(): Boolean

    /**
     * Specify a maximum number of client defined background activities.
     */
    public fun getManualBackgroundActivityLimit(): Int

    /**
     * Specify a minimum duration for a client defined background activity.
     */
    public fun getMinBackgroundActivityDuration(): Long

    /**
     * Specify the max number of background activities cached to disk at the same time.
     */
    public fun getMaxCachedActivities(): Int
}
