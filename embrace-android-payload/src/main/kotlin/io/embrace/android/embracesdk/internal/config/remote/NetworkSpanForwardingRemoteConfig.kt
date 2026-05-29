package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkSpanForwardingRemoteConfig(
    @SerialName("pct_enabled")
    val pctEnabled: Float? = null,
)
