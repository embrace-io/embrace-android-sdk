package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ThermalState(

    @Json(name = "t")
    internal val timestamp: Long,

    @Json(name = "s")
    internal val status: Int
)
