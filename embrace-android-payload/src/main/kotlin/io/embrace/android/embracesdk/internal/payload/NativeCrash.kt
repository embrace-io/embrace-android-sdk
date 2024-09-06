package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NativeCrash(
    @Json(name = "id") val id: String?,
    @Json(name = "m") val crashMessage: String?,
    @Json(name = "sb") val symbols: Map<String?, String?>?,
    @Json(name = "er") val errors: List<NativeCrashDataError?>?,
    @Json(name = "ue") val unwindError: Int?,
    @Json(name = "ma") val map: String?,
    @Json(name = "crash_number") val crashNumber: Int? = null
)
