package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
class AllowedNdkSampleMethod(
    @SerialName("c") @Json(name = "c") val clz: String? = null,
    @SerialName("m") @Json(name = "m") val method: String? = null,
)
