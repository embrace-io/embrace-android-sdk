package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Describes a particular Exception error. Where an exception error has a cause, there will be an
 * {@link ExceptionErrorInfo} for each nested cause.
 */
@JsonClass(generateAdapter = true)
internal data class ExceptionErrorInfo(

    /**
     * Timestamp when exception error happened.
     */
    @Json(name = "ts") val timestamp: Long? = null,

    /**
     * App state (foreground or background).
     */
    @Json(name = "s") val state: String? = null,

    /**
     * A list of exceptions.
     */
    @Json(name = "ex") val exceptions: List<ExceptionInfo>? = null

)
