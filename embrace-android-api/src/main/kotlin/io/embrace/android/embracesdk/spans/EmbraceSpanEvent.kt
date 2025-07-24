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
        /**
         * @suppress
         */
        public fun create(name: String, timestampMs: Long, attributes: Map<String, String>?): EmbraceSpanEvent? {
            return EmbraceSpanEvent(
                name = name,
                timestampNanos = TimeUnit.MILLISECONDS.toNanos(timestampMs),
                attributes = attributes ?: emptyMap()
            )
        }
    }
}
