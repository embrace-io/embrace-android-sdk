package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
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
@JsonClass(generateAdapter = true)
data class SpanEvent(

    /* The name of the event */
    @SerialName("name")
    @Json(name = "name")
    val name: String? = null,

    /* The time the event occurred, in nanoseconds since the Unix epoch */
    @SerialName("time_unix_nano")
    @Json(name = "time_unix_nano")
    val timestampNanos: Long? = null,

    @SerialName("attributes")
    @Json(name = "attributes")
    val attributes: List<Attribute>? = null,
)
