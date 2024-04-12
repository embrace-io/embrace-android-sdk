package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration to switch to OpenTelemetry payloads for session, logs, crashes, etc.
 */
@JsonClass(generateAdapter = true)
internal data class OTelRemoteConfig(
    @Json(name = "stable")
    val isStableEnabled: Boolean? = null,

    @Json(name = "beta")
    val isBetaEnabled: Boolean? = null,

    @Json(name = "dev")
    val isDevEnabled: Boolean? = null,
)
