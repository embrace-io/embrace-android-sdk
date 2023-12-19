package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class WebViewInfo(
    @Json(name = "t")
    val tag: String? = null,

    @Json(name = "vt")
    val webVitals: MutableList<WebVital> = mutableListOf(),

    @Json(name = "u")
    val url: String,

    @Json(name = "ts")
    val startTime: Long,

    @Transient
    val webVitalMap: MutableMap<WebVitalType, WebVital> = hashMapOf()
)
