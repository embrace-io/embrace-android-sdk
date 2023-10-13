package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents the background activity configuration element specified in the Embrace config file.
 */
internal class BackgroundActivityLocalConfig(
    @SerializedName("capture_enabled")
    val backgroundActivityCaptureEnabled: Boolean? = null,

    @SerializedName("manual_background_activity_limit")
    val manualBackgroundActivityLimit: Int? = null,

    @SerializedName("min_background_activity_duration")
    val minBackgroundActivityDuration: Long? = null,

    @SerializedName("max_cached_activities")
    val maxCachedActivities: Int? = null
)
