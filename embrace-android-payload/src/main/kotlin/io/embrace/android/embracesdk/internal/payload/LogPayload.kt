package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A set of log messages sent by the user
 *
 * @param logs
 */
@Serializable
@JsonClass(generateAdapter = true)
data class LogPayload(

    @SerialName("logs")
    @Json(name = "logs")
    val logs: List<Log>? = null,
)
