package io.embrace.android.embracesdk.spans

import java.util.concurrent.TimeUnit

/**
 * Represents an Event in an [EmbraceSpan]
 */
public class EmbraceSpanEvent internal constructor(
    /**
     * The name of the event
     */
    public val name: String,

    /**
     * The timestamp of the event in nanoseconds
     */
    public val timestampNanos: Long,

    /**
     * The attributes of this event
     */
    public val attributes: Map<String, String>,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbraceSpanEvent

        if (timestampNanos != other.timestampNanos) return false
        if (name != other.name) return false
        if (attributes != other.attributes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampNanos.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }
}
