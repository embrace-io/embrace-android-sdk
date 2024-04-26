package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class ThermalState(

    @Json(name = "t")
    val timestamp: Long,

    @Json(name = "s")
    val status: Int
)
