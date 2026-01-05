package io.embrace.android.embracesdk.internal.telemetry

/**
 * Represents the type of limit that was applied to telemetry.
 */
enum class AppliedLimitType(val attributeName: String) {
    /**
     * Attributes on a telemetry item were truncated due to attribute count limits.
     */
    TRUNCATE_ATTRIBUTES("truncate_attributes"),

    /**
     * A string value was truncated due to string length limits.
     */
    TRUNCATE_STRING("truncate_string"),

    /**
     * A telemetry item was completely dropped due to rate limits or other constraints.
     */
    DROP("drop")
}
