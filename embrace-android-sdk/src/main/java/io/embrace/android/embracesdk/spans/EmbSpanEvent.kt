package io.embrace.android.embracesdk.spans

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents an Event in an [EmbraceSpan]
 */
@JsonClass(generateAdapter = true)
internal data class EmbSpanEvent internal constructor(
    /**
     * The name of the event
     */
    @Json(name = "name")
    val name: String,

    /**
     * The timestamp of the event in nanoseconds
     */
    @Json(name = "time_unix_nano")
    val timestampNanos: Long,

    /**
     * The attributes of this event
     */
    @Json(name = "attributes")
    val attributes: Map<String, String>
)

internal fun EmbraceSpanEvent.toEmbSpanEvent() = EmbSpanEvent(
    name = this.name,
    timestampNanos = this.timestampNanos,
    attributes = this.attributes
)
