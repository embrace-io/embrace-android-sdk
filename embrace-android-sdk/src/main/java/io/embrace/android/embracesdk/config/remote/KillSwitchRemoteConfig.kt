package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * This contains config values which can turn risky functionality completely off.
 * In normal circumstances these should never actually be used 🤞.
 */
@JsonClass(generateAdapter = true)
internal data class KillSwitchRemoteConfig(
    @Json(name = "sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,
    @Json(name = "jetpack_compose")
    val jetpackCompose: Boolean? = null
)
