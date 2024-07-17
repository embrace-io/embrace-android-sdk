package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class WebViewLocalConfig(
    @Json(name = "enable")
    public val captureWebViews: Boolean? = null,

    @Json(name = "capture_query_params")
    public val captureQueryParams: Boolean? = null
)
