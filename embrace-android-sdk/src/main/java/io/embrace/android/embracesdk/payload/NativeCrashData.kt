package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal class NativeCrashData(
    @SerializedName("report_id") val nativeCrashId: String,
    @SerializedName("sid") val sessionId: String,
    @SerializedName("ts") val timestamp: Long,
    @SerializedName("state") val appState: String?,
    @SerializedName("meta") val metadata: NativeCrashMetadata?,
    @SerializedName("ue") val unwindError: Int?,
    @SerializedName("crash") private val crash: String?,
    @SerializedName("symbols") var symbols: Map<String?, String?>?,
    @SerializedName("errors") var errors: List<NativeCrashDataError?>?,
    @SerializedName("map") var map: String?
) {

    fun getCrash() = NativeCrash(nativeCrashId, crash, symbols, errors, unwindError, map)
}
