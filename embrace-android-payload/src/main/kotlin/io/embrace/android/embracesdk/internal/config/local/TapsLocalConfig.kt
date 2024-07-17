package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class TapsLocalConfig(
    @Json(name = "capture_coordinates")
    public val captureCoordinates: Boolean? = null
)
