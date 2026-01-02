package io.embrace.android.embracesdk.internal.telemetry

/**
 * Represents the type of telemetry that had a limit applied to it.
 */
enum class LimitedTelemetryType(val attributeName: String) {
    BREADCRUMB("breadcrumb"),
    TAP_DATA_SOURCE("tap_data_source"),
    COMPOSE_TAP_DATA_SOURCE("compose_tap_data_source"),
    WEBVIEW_URL_DATA_SOURCE("webview_url_data_source"),
    PUSH_NOTIFICATION_DATA_SOURCE("push_notification_data_source"),
    VIEW_DATA_SOURCE("view_data_source"),
    LOW_POWER_DATA_SOURCE("low_power_data_source"),
    POWER_STATE_DATA_SOURCE("power_state_data_source"),
    THERMAL_STATE_DATA_SOURCE("thermal_state_data_source"),
    NETWORK_STATE_DATA_SOURCE("network_state_data_source"),
    NETWORK_STATUS_DATA_SOURCE("network_status_data_source"),
    INTERNAL_ERROR_DATA_SOURCE("internal_error_data_source"),
    AEI_DATA_SOURCE("aei_data_source")
}
