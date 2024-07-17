package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class ViewLocalConfig(

    @Json(name = "enable_automatic_activity_capture")
    public val enableAutomaticActivityCapture: Boolean? = null
)
