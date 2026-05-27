package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class NetworkSpanForwardingRemoteConfig(
    @SerialName("pct_enabled")
    @Json(name = "pct_enabled")
    val pctEnabled: Float? = null,
)
