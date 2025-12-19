package io.embrace.android.embracesdk.internal.telemetry

/**
 * Represents the type of limit that was applied to telemetry.
 */
enum class AppliedLimitType {
    /**
     * Attributes on a telemetry item were truncated due to attribute count limits.
     */
    TRUNCATE_ATTRIBUTES,

    /**
     * A string value was truncated due to string length limits.
     */
    TRUNCATE_STRING,

    /**
     * A telemetry item was completely dropped due to rate limits or other constraints.
     */
    DROP;

    /**
     * Converts the enum to its attribute name format (lowercase with underscores).
     */
    fun toAttributeName(): String = name.lowercase()
}
