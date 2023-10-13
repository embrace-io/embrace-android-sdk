package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Web Vitals are a set of performance metrics that measure and report on the speed and quality of web pages.
 */
internal data class WebVital(

    @SerializedName("t")
    val type: WebVitalType,

    @SerializedName("n")
    val name: String,

    @SerializedName("st")
    val startTime: Long,

    @SerializedName("d")
    val duration: Long,

    @SerializedName("p")
    val properties: Map<String, Any>,

    @SerializedName("s")
    val score: Double
)
