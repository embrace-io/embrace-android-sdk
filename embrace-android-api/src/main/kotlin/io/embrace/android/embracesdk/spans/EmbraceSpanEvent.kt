package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.annotation.BetaApi
import java.util.concurrent.TimeUnit

/**
 * Represents an Event in an [EmbraceSpan]
 */
@BetaApi
public data class EmbraceSpanEvent internal constructor(
    /**
     * The name of the event
     */
    val name: String,

    /**
     * The timestamp of the event in nanoseconds
     */
    val timestampNanos: Long,

    /**
     * The attributes of this event
     */
    val attributes: Map<String, String>
) {
    public companion object {
        internal const val MAX_EVENT_NAME_LENGTH = 100
        internal const val MAX_EVENT_ATTRIBUTE_COUNT = 10

        public fun create(name: String, timestampMs: Long, attributes: Map<String, String>?): EmbraceSpanEvent? {
            if (inputsValid(name, attributes)) {
                val time = TimeUnit.NANOSECONDS.toMillis(timestampMs)
                return EmbraceSpanEvent(name = name, timestampNanos = time, attributes = attributes ?: emptyMap())
            }

            return null
        }

        internal fun inputsValid(name: String, attributes: Map<String, String>?) =
            name.length <= MAX_EVENT_NAME_LENGTH && (attributes == null || attributes.size <= MAX_EVENT_ATTRIBUTE_COUNT)
    }
}
