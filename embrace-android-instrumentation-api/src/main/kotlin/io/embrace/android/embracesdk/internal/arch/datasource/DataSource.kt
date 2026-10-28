package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace. It attempts to enforce limits and input validation
 * by default.
 *
 * [DataSourceFactory] is closely related. It creates a data source, or returns null if the SDK's
 * configuration disables it.
 */
interface DataSource {

    /**
     * The name used to identify this data source for telemetry and tracking purposes.
     * Should be in snake_case format and be unique (e.g., "tap_data_source").
     */
    val instrumentationName: String

    /**
     * Enables data capture. This should include registering any listeners, and resetting
     * any state (if applicable).
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     *
     * This runs while the instrumentation registry holds the lock every other data source's registration needs,
     * including those made on the main thread while the SDK starts, so it must return quickly. Submit binder calls,
     * I/O and other slow work to a background worker instead, as the power save and thermal state data sources do
     * for their system registrations.
     */
    fun onDataCaptureEnabled()

    /**
     * Permanently disables data capture because the SDK has been disabled. This should stop anything that would
     * otherwise capture data or write to disk outside of [captureTelemetry], such as crash handlers.
     *
     * Currently, it is also the job of this method to clean up any resources allocated in the construction of the
     * data source via [DataSourceFactory], regardless of whether [onDataCaptureEnabled] ever ran. This should be
     * changed later so that the responsibility of this method is narrowed.
     */
    fun onDataCaptureDisabled()

    /**
     * Resets any data capture limits since the last time [onDataCaptureEnabled] was called.
     */
    fun resetDataCaptureLimits()

    /**
     * Captures telemetry from the given action, if the [DataSourceFactory] and limits allow it.
     */
    fun <T> captureTelemetry(
        inputValidation: () -> Boolean = { true },
        invalidInputCallback: () -> Unit = {},
        action: TelemetryDestination.() -> T?,
    ): T?
}
