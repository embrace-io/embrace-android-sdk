package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class WebViewVitals @JvmOverloads constructor(
    @Json(name = "pct_enabled")
    val pctEnabled: Float? = null,

    @Json(name = "max_vitals")
    val maxVitals: Int? = null
)
