package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the background activity configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
internal class BackgroundActivityLocalConfig(
    @Json(name = "capture_enabled")
    val backgroundActivityCaptureEnabled: Boolean? = null,

    @Json(name = "manual_background_activity_limit")
    val manualBackgroundActivityLimit: Int? = null,

    @Json(name = "min_background_activity_duration")
    val minBackgroundActivityDuration: Long? = null,

    @Json(name = "max_cached_activities")
    val maxCachedActivities: Int? = null
)
