package io.embrace.android.embracesdk.spans

import java.util.concurrent.TimeUnit

/**
 * Represents an Event in an [EmbraceSpan]
 */
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
    val attributes: Map<String, String>,
) {

    /**
     * @suppress
     */
    public companion object {
        internal const val MAX_EVENT_NAME_LENGTH = 100
        internal const val MAX_EVENT_ATTRIBUTE_COUNT = 10

        /**
         * @suppress
         */
        public fun create(name: String, timestampMs: Long, attributes: Map<String, String>?): EmbraceSpanEvent? {
            if (inputsValid(name, attributes)) {
                return EmbraceSpanEvent(
                    name = name,
                    timestampNanos = TimeUnit.MILLISECONDS.toNanos(timestampMs),
                    attributes = attributes ?: emptyMap()
                )
            }

            return null
        }

        internal fun inputsValid(name: String, attributes: Map<String, String>?) =
            name.length <= MAX_EVENT_NAME_LENGTH && (attributes == null || attributes.size <= MAX_EVENT_ATTRIBUTE_COUNT)
    }
}
