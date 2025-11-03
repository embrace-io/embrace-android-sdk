package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace. It attempts to enforce limits and input validation
 * by default.
 *
 * [DataSourceState] is closely related. It is responsible for determining when a data source
 * should be turned on/off, according to the SDK's configuration and the process lifecycle.
 */
interface DataSource : CrashTeardownHandler {

    /**
     * Enables data capture. This should include registering any listeners, and resetting
     * any state (if applicable).
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     */
    fun onDataCaptureEnabled()

    /**
     * Disables data capture. This should include unregistering any listeners, and resetting
     * any state (if applicable).
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     */
    fun onDataCaptureDisabled()

    /**
     * Resets any data capture limits since the last time [onDataCaptureEnabled] was called.
     */
    fun resetDataCaptureLimits()

    /**
     * Captures telemetry from the given action, if the [DataSourceState] and limits allow it.
     */
    fun captureTelemetry(
        inputValidation: () -> Boolean = { true },
        action: TelemetryDestination.() -> Unit,
    )
}
