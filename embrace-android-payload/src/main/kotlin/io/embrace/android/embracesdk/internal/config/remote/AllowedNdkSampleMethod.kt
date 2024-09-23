package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class AllowedNdkSampleMethod(
    @Json(name = "c") val clz: String? = null,
    @Json(name = "m") val method: String? = null
)
