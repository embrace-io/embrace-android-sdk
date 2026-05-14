package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace. It attempts to enforce limits and input validation
 * by default.
 *
 * Implementations that use [DataSourceState] to manage their lifecycles should extend [DataSourceImpl], as they work in tandem
 * to provide and enforce the proper creation/enablement steps.
 */
interface DataSource {

    /**
     * The name used to identify this data source for telemetry and tracking purposes.
     * Should be in snake_case format and be unique (e.g., "tap_data_source").
     */
    val instrumentationName: String

    /**
     * Enables this data source for data capture. This should include registering any listeners, and resetting any state (if applicable).
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     */
    fun onDataCaptureEnabled()

    /**
     * Disables data capture. This should include unregistering any listeners, and resetting any state (if applicable).
     *
     * Disabling data capture does not make this data source not enabled.
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     */
    fun onDataCaptureDisabled()

    /**
     * Resets any data capture limits since the last time [onDataCaptureEnabled] was called.
     */
    fun resetDataCaptureLimits()

    /**
     * Captures telemetry from the given action, if the [DataSourceState] limits allow it.
     */
    fun <T> captureTelemetry(
        inputValidation: () -> Boolean = { true },
        invalidInputCallback: () -> Unit = {},
        action: TelemetryDestination.() -> T?,
    ): T?
}
