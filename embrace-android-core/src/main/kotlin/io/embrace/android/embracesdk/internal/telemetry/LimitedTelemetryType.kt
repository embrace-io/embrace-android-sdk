package io.embrace.android.embracesdk.internal.telemetry

/**
 * Represents the type of telemetry that had a limit applied to it.
 */
enum class LimitedTelemetryType(val attributeName: String) {
    EXCEPTION("exception"),
    ERROR_LOG("error_log"),
    WARNING_LOG("warning_log"),
    INFO_LOG("info_log"),
    BREADCRUMB("breadcrumb"),
    SESSION_PROPERTY("session_property"),
    SESSION_PROPERTY_KEY("session_property_key"),
    SESSION_PROPERTY_VALUE("session_property_value"),
    LOG_ATTRIBUTE_KEY("log_attribute_key"),
    LOG_ATTRIBUTE_VALUE("log_attribute_value"),
    EXCEPTION_ATTRIBUTE_KEY("exception_attribute_key"),
    EXCEPTION_ATTRIBUTE_VALUE("exception_attribute_value"),
    SPAN("span"),
    NETWORK_REQUEST("network_request")
}
