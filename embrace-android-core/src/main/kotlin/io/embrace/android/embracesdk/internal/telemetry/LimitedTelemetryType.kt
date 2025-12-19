package io.embrace.android.embracesdk.internal.telemetry

/**
 * Represents the type of telemetry that had a limit applied to it.
 */
enum class LimitedTelemetryType {
    EXCEPTION,
    ERROR_LOG,
    WARNING_LOG,
    INFO_LOG,
    BREADCRUMB,
    SESSION_PROPERTY,
    SESSION_PROPERTY_KEY,
    SESSION_PROPERTY_VALUE,
    LOG_ATTRIBUTE_KEY,
    LOG_ATTRIBUTE_VALUE,
    EXCEPTION_ATTRIBUTE_KEY,
    EXCEPTION_ATTRIBUTE_VALUE,
    SPAN,
    NETWORK_REQUEST;

    /**
     * Converts the enum to its attribute name format (lowercase with underscores).
     */
    fun toAttributeName(): String = name.lowercase()
}
