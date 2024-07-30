package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class NativeCrashData(
    @Json(name = "report_id") public val nativeCrashId: String,
    @Json(name = "sid") public val sessionId: String,
    @Json(name = "ts") public val timestamp: Long,
    @Json(name = "state") public val appState: String?,
    @Json(name = "meta") public val metadata: NativeCrashMetadata?,
    @Json(name = "ue") public val unwindError: Int?,
    @Json(name = "crash") public val crash: String?,
    @Json(name = "symbols") public var symbols: Map<String?, String?>?,
    @Json(name = "errors") public var errors: List<NativeCrashDataError?>?,
    @Json(name = "map") public var map: String?
) {

    public fun getCrash(crashNumber: Int): NativeCrash = NativeCrash(nativeCrashId, crash, symbols, errors, unwindError, map, crashNumber)
}
