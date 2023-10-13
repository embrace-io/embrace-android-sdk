package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * This contains config values which can turn risky functionality completely off.
 * In normal circumstances these should never actually be used 🤞.
 */
internal data class KillSwitchRemoteConfig(
    @SerializedName("sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,
    @SerializedName("jetpack_compose")
    val jetpackCompose: Boolean? = null
)
