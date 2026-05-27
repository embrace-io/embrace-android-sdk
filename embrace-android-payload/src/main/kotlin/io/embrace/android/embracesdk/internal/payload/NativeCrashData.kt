package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class NativeCrashData(
    @SerialName("report_id") @Json(name = "report_id") val nativeCrashId: String,
    @SerialName("sid") @Json(name = "sid") val sessionId: String,
    @SerialName("ts") @Json(name = "ts") val timestamp: Long,
    @SerialName("crash") @Json(name = "crash") val crash: String?,
    @SerialName("symbols") @Json(name = "symbols") var symbols: Map<String, String>?,
)
