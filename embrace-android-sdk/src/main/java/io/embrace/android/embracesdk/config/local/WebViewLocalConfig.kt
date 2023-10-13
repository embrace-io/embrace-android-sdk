package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class WebViewLocalConfig(
    @SerializedName("enable")
    val captureWebViews: Boolean? = null,

    @SerializedName("capture_query_params")
    val captureQueryParams: Boolean? = null
)
