package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AllowedNdkSampleMethod(
    @SerialName("c") val clz: String? = null,
    @SerialName("m") val method: String? = null,
)
