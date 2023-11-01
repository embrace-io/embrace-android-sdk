package io.embrace.android.embracesdk.spans

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.annotation.BetaApi

/**
 * Represents an Event in an [EmbraceSpan]
 */
@BetaApi
public data class EmbraceSpanEvent internal constructor(
    /**
     * The name of the event
     */
    @SerializedName("name")
    val name: String,

    /**
     * The timestamp of the event in nanoseconds
     */
    @SerializedName("time_unix_nano")
    val timestampNanos: Long,

    /**
     * The attributes of this event
     */
    @SerializedName("attributes")
    val attributes: Map<String, String>
) {
    public companion object {
        internal const val MAX_EVENT_NAME_LENGTH = 100
        internal const val MAX_EVENT_ATTRIBUTE_COUNT = 10

        public fun create(name: String, timestampNanos: Long, attributes: Map<String, String>?): EmbraceSpanEvent? {
            if (inputsValid(name, attributes)) {
                return EmbraceSpanEvent(name = name, timestampNanos = timestampNanos, attributes = attributes ?: emptyMap())
            }

            return null
        }

        internal fun inputsValid(name: String, attributes: Map<String, String>?) =
            name.length <= MAX_EVENT_NAME_LENGTH && (attributes == null || attributes.size <= MAX_EVENT_ATTRIBUTE_COUNT)
    }
}
