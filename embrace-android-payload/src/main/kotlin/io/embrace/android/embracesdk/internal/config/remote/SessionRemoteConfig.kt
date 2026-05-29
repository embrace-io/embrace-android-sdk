package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * It serves as a session controller components. It determines if session may be ended in
 * the background. It also determines which components will be sent as part of the
 * session payload. This feature may be enabled/disabled.
 */
@Serializable
data class SessionRemoteConfig(
    @SerialName("enable")
    val isEnabled: Boolean? = null,
)
