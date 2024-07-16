package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class NetworkSpanForwardingRemoteConfig(
    @Json(name = "pct_enabled")
    val pctEnabled: Float? = null
)
