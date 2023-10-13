package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class WebViewInfo(
    @SerializedName("t")
    val tag: String,

    @SerializedName("vt")
    val webVitals: MutableList<WebVital> = mutableListOf(),

    @SerializedName("u")
    val url: String,

    @SerializedName("ts")
    val startTime: Long,

    @Transient
    val webVitalMap: MutableMap<WebVitalType, WebVital> = hashMapOf()
)
