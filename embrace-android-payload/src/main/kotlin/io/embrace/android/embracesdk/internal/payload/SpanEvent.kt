package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * An event that occurred during a span
 *
 * @param name The name of the event
 * @param timestampNanos The time the event occurred, in nanoseconds since the Unix epoch
 * @param attributes
 */
@JsonClass(generateAdapter = true)
data class SpanEvent(

    /* The name of the event */
    @Json(name = "name")
    val name: String? = null,

    /* The time the event occurred, in nanoseconds since the Unix epoch */
    @Json(name = "time_unix_nano")
    val timestampNanos: Long? = null,

    @Json(name = "attributes")
    val attributes: List<Attribute>? = null,
)
