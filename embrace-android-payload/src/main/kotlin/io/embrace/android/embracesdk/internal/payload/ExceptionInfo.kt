package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Describes a Java exception.
 *
 * @param name The name of the class causing an error. Previous name: s.e.rep.ex.n
 * @param message The error message, if any. Previous name: s.e.rep.ex.m
 * @param stacktrace String representation of each line in the stack trace. Previous name: s.e.rep.ex.tt
 */
@JsonClass(generateAdapter = true)
data class ExceptionInfo(

    /* The name of the class causing an error. Previous name: s.e.rep.ex.n */
    @Json(name = "name")
    val name: String? = null,

    /* The error message, if any. Previous name: s.e.rep.ex.m */
    @Json(name = "message")
    val message: String? = null,

    /* String representation of each line in the stack trace. Previous name: s.e.rep.ex.tt */
    @Json(name = "stacktrace")
    val stacktrace: List<String>? = null

)
