package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json

/**
 * A set of log messages sent by the user
 *
 * @param logs
 */
internal data class LogPayload(

    @Json(name = "logs")
    val logs: List<Log>? = null
)
