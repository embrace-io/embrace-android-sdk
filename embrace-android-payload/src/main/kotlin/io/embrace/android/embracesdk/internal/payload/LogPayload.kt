package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A set of log messages sent by the user
 *
 * @param logs
 */
@Serializable
data class LogPayload(

    @SerialName("logs")
    val logs: List<Log>? = null,
)
