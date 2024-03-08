package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A set of log messages sent by the user
 *
 * @param logs
 */
@JsonClass(generateAdapter = true)
internal data class LogPayload(

    @Json(name = "logs")
    val logs: List<Log>? = null
)
