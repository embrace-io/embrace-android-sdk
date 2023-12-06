package io.embrace.android.embracesdk.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class ViewLocalConfig(

    @Json(name = "enable_automatic_activity_capture")
    val enableAutomaticActivityCapture: Boolean? = null
)
