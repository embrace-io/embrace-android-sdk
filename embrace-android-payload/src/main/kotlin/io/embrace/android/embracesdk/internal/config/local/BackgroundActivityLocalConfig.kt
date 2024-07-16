package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the background activity configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
public class BackgroundActivityLocalConfig(
    @Json(name = "capture_enabled")
    public val backgroundActivityCaptureEnabled: Boolean? = null,

    @Json(name = "manual_background_activity_limit")
    public val manualBackgroundActivityLimit: Int? = null,

    @Json(name = "min_background_activity_duration")
    public val minBackgroundActivityDuration: Long? = null,

    @Json(name = "max_cached_activities")
    public val maxCachedActivities: Int? = null
)
