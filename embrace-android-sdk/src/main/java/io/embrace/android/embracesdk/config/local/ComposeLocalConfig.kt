package io.embrace.android.embracesdk.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class ComposeLocalConfig(
    @Json(name = "capture_compose_onclick")
    val captureComposeOnClick: Boolean? = null
)
