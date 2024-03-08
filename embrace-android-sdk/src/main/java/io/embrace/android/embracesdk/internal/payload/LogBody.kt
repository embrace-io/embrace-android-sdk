package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A value containing the body of the log record
 *
 * @param message The log message
 */
@JsonClass(generateAdapter = true)
internal data class LogBody(

    /* The log message */
    @Json(name = "message")
    val message: String? = null
)
