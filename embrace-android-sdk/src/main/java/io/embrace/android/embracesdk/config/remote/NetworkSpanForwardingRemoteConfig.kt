package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

internal data class NetworkSpanForwardingRemoteConfig(
    @SerializedName("pct_enabled")
    val pctEnabled: Float? = null
)
