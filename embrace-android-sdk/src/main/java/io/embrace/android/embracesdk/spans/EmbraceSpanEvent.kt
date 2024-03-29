package io.embrace.android.embracesdk.spans

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.annotation.BetaApi
import io.embrace.android.embracesdk.internal.clock.millisToNanos

/**
 * Represents an Event in an [EmbraceSpan]
 */
@BetaApi
@JsonClass(generateAdapter = true)
public data class EmbraceSpanEvent internal constructor(
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
) {
    public companion object {
        internal const val MAX_EVENT_NAME_LENGTH = 100
        internal const val MAX_EVENT_ATTRIBUTE_COUNT = 10

        public fun create(name: String, timestampMs: Long, attributes: Map<String, String>?): EmbraceSpanEvent? {
            if (inputsValid(name, attributes)) {
                return EmbraceSpanEvent(name = name, timestampNanos = timestampMs.millisToNanos(), attributes = attributes ?: emptyMap())
            }

            return null
        }

        internal fun inputsValid(name: String, attributes: Map<String, String>?) =
            name.length <= MAX_EVENT_NAME_LENGTH && (attributes == null || attributes.size <= MAX_EVENT_ATTRIBUTE_COUNT)
    }
}
