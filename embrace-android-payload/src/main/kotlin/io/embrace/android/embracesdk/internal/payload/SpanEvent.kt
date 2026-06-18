package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An event that occurred during a span
 *
 * @param name The name of the event
 * @param timestampNanos The time the event occurred, in nanoseconds since the Unix epoch
 * @param attributes
 */
@Serializable
data class SpanEvent(

    /* The name of the event */
    @SerialName("name")
    val name: String? = null,

    /* The time the event occurred, in nanoseconds since the Unix epoch */
    @SerialName("time_unix_nano")
    val timestampNanos: Long? = null,

    @SerialName("attributes")
    val attributes: List<Attribute>? = null,
)
