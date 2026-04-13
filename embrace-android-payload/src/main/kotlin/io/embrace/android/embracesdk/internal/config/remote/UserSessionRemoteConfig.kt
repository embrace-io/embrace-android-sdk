package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Remote configuration for user session behavior, including maximum session duration and
 * inactivity timeout.
 */
@JsonClass(generateAdapter = true)
data class UserSessionRemoteConfig(

    @Json(name = "max_duration_seconds")
    val maxDurationSeconds: Int? = null,

    @Json(name = "inactivity_timeout_seconds")
    val inactivityTimeoutSeconds: Int? = null,
)
