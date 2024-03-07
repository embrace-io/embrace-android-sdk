package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json

/**
 * A value containing the body of the log record
 *
 * @param message The log message
 */
internal data class LogBody(

    /* The log message */
    @Json(name = "message")
    val message: String? = null
)
