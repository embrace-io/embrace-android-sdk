package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * It serves as a session controller components. It determines if session may be ended in
 * the background. It also determines which components will be sent as part of the
 * session payload. This feature may be enabled/disabled.
 */
@JsonClass(generateAdapter = true)
data class SessionRemoteConfig(
    @Json(name = "enable")
    val isEnabled: Boolean? = null,
)
