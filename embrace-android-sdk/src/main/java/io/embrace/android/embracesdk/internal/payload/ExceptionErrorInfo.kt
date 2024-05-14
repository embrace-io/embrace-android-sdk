package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * @param timestamp The timestamp in milliseconds of when an error happened. Previous name: s.e.rep.ts
 * @param exceptions A list of exceptions. Previous name: s.e.rep.ex
 */
@JsonClass(generateAdapter = true)
internal data class ExceptionErrorInfo(

    /* The timestamp in milliseconds of when an error happened. Previous name: s.e.rep.ts */
    @Json(name = "timestamp")
    val timestamp: Long? = null,

    /* A list of exceptions. Previous name: s.e.rep.ex */
    @Json(name = "exceptions")
    val exceptions: List<ExceptionInfo>? = null
)
