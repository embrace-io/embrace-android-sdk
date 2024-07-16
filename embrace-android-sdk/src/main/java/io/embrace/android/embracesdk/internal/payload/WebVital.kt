package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Web Vitals are a set of performance metrics that measure and report on the speed and quality of web pages.
 */
@JsonClass(generateAdapter = true)
internal data class WebVital(

    @Json(name = "t")
    val type: WebVitalType,

    @Json(name = "n")
    val name: String,

    @Json(name = "st")
    val startTime: Long,

    @Json(name = "d")
    val duration: Long? = null,

    @Json(name = "p")
    val properties: Map<String, Any>? = null,

    @Json(name = "s")
    val score: Double? = null
)
