package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class NetworkSpanForwardingRemoteConfig(
    @Json(name = "pct_enabled")
    val pctEnabled: Float? = null
)
