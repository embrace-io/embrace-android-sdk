package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NativeCrashData(
    @Json(name = "report_id") val nativeCrashId: String,
    @Json(name = "sid") val sessionId: String,
    @Json(name = "ts") val timestamp: Long,
    @Json(name = "crash") val crash: String?,
    @Json(name = "symbols") var symbols: Map<String, String>?,
)
