package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Describes an Exception Error with a count of occurrences and a list of exceptions (causes).
 *
 * @param count The number of internal error that occurred within Embrace. Previous name: s.e.c
 * @param errors A list of causes of the internal error. Previous name: s.e.rep
 */
@JsonClass(generateAdapter = true)
data class InternalError(

    /* The number of internal error that occurred within Embrace. Previous name: s.e.c */
    @Json(name = "count")
    val count: Int? = null,

    /* A list of causes of the internal error. Previous name: s.e.rep */
    @Json(name = "errors")
    val errors: List<ExceptionErrorInfo>? = null

)
