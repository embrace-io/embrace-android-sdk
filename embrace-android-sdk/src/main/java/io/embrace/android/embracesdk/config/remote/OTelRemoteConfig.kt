package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration to switch to OpenTelemetry payloads for session, logs, crashes, etc.
 */
@JsonClass(generateAdapter = true)
internal data class OTelRemoteConfig(
    @Json(name = "use_v2_session_payload")
    val useV2SessionPayload: Boolean? = null,

    @Json(name = "use_v2_log_payload")
    val useV2LogPayload: Boolean? = null,

    @Json(name = "use_v2_crash_payload")
    val useV2CrashPayload: Boolean? = null,
)
