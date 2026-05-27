package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote configuration for user session behavior, including maximum session duration and
 * inactivity timeout.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class UserSessionRemoteConfig(

    @SerialName("max_duration_seconds")
    @Json(name = "max_duration_seconds")
    val maxDurationSeconds: Int? = null,

    @SerialName("inactivity_timeout_seconds")
    @Json(name = "inactivity_timeout_seconds")
    val inactivityTimeoutSeconds: Int? = null,
)
