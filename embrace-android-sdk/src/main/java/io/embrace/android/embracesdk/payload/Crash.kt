package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Crash(
    @Json(name = "id")
    @JvmField
    val crashId: String,

    @Json(name = "ex")
    val exceptions: List<ExceptionInfo>? = null,

    @Json(name = "rep_js")
    val jsExceptions: List<String>? = null,

    @Json(name = "th")
    val threads: List<ThreadInfo>? = null
)
