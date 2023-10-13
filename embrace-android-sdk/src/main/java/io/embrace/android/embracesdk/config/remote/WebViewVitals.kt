package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

internal data class WebViewVitals @JvmOverloads constructor(
    @SerializedName("pct_enabled")
    val pctEnabled: Float? = null,

    @SerializedName("max_vitals")
    val maxVitals: Int? = null
)
