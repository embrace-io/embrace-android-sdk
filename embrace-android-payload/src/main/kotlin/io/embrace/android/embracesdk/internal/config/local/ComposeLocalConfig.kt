package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class ComposeLocalConfig(
    @Json(name = "capture_compose_onclick")
    public val captureComposeOnClick: Boolean? = null
)
