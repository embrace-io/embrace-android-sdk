package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * It serves as a session controller components. It determines if session may be ended in
 * the background. It also determines which components will be sent as part of the
 * session payload. This feature may be enabled/disabled.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class SessionRemoteConfig(
    @SerialName("enable")
    @Json(name = "enable")
    val isEnabled: Boolean? = null,
)
