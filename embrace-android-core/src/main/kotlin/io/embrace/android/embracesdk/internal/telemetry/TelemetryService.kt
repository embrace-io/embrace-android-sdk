package io.embrace.android.embracesdk.internal.telemetry

interface TelemetryService {

    /**
     * Tracks the usage of a public API by name. We only track public APIs that are called when the SDK is initialized.
     * Name should be snake_case, e.g. "start_session".
     */
    fun onPublicApiCalled(name: String)

    /**
     * Tracks the storage being used, in bytes. storageTelemetry is a map of storage telemetry names and their values in bytes,
     * such as: emb.storage.usage -> "1234"
     */
    fun logStorageTelemetry(storageTelemetry: Map<String, String>)

    /**
     * Tracks when a limit is applied to telemetry (truncation or drop).
     *
     * @param telemetryType The type of telemetry that had the limit applied
     * @param limitType The type of limit that was applied
     */
    fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType)

    /**
     * Returns a map with every telemetry value. This is called when the session ends.
     * We clear the usage count map so we don't count the same usages in the next session.
     */
    fun getAndClearTelemetryAttributes(): Map<String, String>
}
