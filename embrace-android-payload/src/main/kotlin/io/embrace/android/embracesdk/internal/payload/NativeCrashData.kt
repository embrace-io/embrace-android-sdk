package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NativeCrashData(
    @SerialName("report_id") val nativeCrashId: String,
    @SerialName("sid") val sessionPartId: String,
    @SerialName("usid") val userSessionId: String = "",
    @SerialName("ts") val timestamp: Long,
    @SerialName("crash") val crash: String?,
    @SerialName("symbols") var symbols: Map<String, String>?,
)
