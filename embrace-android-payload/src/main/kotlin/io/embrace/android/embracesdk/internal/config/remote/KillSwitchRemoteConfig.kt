package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * This contains config values which can turn risky functionality completely off.
 * In normal circumstances these should never actually be used 🤞.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class KillSwitchRemoteConfig(

    @SerialName("sig_handler_detection")
    @Json(name = "sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,

    @SerialName("jetpack_compose")
    @Json(name = "jetpack_compose")
    val jetpackCompose: Boolean? = null,
)
