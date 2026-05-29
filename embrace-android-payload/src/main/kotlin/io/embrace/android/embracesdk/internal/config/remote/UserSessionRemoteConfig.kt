package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Remote configuration for user session behavior, including maximum session duration and
 * inactivity timeout.
 */
@Serializable
data class UserSessionRemoteConfig(

    @SerialName("max_duration_seconds")
    val maxDurationSeconds: Int? = null,

    @SerialName("inactivity_timeout_seconds")
    val inactivityTimeoutSeconds: Int? = null,
)
