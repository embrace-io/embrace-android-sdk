package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configuration values for the spans feature
 */
internal data class SpansRemoteConfig(
    @SerializedName("pct_enabled")
    val pctEnabled: Float? = null
)
