package io.embrace.android.embracesdk.internal.config.instrumented

/**
 * Declares the behavior for background activities in the SDK
 */
@Suppress("FunctionOnlyReturningConstant")
@Swazzled
object BackgroundActivityConfig {

    /**
     * Declares the limit for background activity capture.
     *
     * sdk_config.background_activity.manual_background_activity_limit
     */
    fun getManualBackgroundActivityLimit(): Int = 100

    /**
     * Declares the minimum duration for background activity capture
     *
     * sdk_config.background_activity.min_background_activity_duration
     */
    fun getMinBackgroundActivityDuration(): Long = 5000L

    /**
     * Declares the maximum number of cached activities
     *
     * sdk_config.background_activity.max_cached_activities
     */
    fun getMaxCachedActivities(): Int = 30
}
