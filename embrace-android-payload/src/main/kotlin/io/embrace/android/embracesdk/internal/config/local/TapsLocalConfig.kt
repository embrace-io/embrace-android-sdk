package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class TapsLocalConfig(
    @Json(name = "capture_coordinates") val captureCoordinates: Boolean? = null
)
