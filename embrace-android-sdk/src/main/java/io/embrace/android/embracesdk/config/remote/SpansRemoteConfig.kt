package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.InternalApi

/**
 * Configuration values for the spans feature
 */
@InternalApi
internal data class SpansRemoteConfig(
    @SerializedName("pct_enabled")
    val pctEnabled: Float? = null
)
