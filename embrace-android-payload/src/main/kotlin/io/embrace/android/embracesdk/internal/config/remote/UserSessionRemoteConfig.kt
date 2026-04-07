package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Remote configuration for user session behavior, including maximum session duration and
 * inactivity timeout.
 */
@JsonClass(generateAdapter = true)
data class UserSessionRemoteConfig(

    @Json(name = "max_duration_minutes")
    val maxDurationMinutes: Int? = null,

    @Json(name = "inactivity_timeout_minutes")
    val inactivityTimeoutMinutes: Int? = null,
)
