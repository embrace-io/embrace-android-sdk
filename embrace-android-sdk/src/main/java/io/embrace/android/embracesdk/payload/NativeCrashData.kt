package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class NativeCrashData(
    @Json(name = "report_id") val nativeCrashId: String,
    @Json(name = "sid") val sessionId: String,
    @Json(name = "ts") val timestamp: Long,
    @Json(name = "state") val appState: String?,
    @Json(name = "meta") val metadata: NativeCrashMetadata?,
    @Json(name = "ue") val unwindError: Int?,
    @Json(name = "crash") internal val crash: String?,
    @Json(name = "symbols") var symbols: Map<String?, String?>?,
    @Json(name = "errors") var errors: List<NativeCrashDataError?>?,
    @Json(name = "map") var map: String?
) {

    fun getCrash(crashNumber: Int) = NativeCrash(nativeCrashId, crash, symbols, errors, unwindError, map, crashNumber)
}
