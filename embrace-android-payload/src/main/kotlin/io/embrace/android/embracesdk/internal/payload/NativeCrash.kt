package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class NativeCrash(
    @Json(name = "id") public val id: String?,
    @Json(name = "m") public val crashMessage: String?,
    @Json(name = "sb") public val symbols: Map<String?, String?>?,
    @Json(name = "er") public val errors: List<NativeCrashDataError?>?,
    @Json(name = "ue") public val unwindError: Int?,
    @Json(name = "ma") public val map: String?,
    @Json(name = "crash_number") public val crashNumber: Int? = null
)
