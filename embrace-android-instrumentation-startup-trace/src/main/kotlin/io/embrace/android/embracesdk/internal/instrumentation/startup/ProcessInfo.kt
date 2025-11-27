package io.embrace.android.embracesdk.internal.instrumentation.startup

/**
 * Runtime-agnostic interface for getting information from the Android Process API
 */
internal interface ProcessInfo {
    /**
     * Return the best-available estimated time for the when the app process was requested to fork
     */
    fun startRequestedTimeMs(): Long?
}
