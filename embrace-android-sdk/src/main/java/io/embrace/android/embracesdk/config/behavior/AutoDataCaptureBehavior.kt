package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface AutoDataCaptureBehavior {

    /**
     * Returns true if [io.embrace.android.embracesdk.MemoryService] should
     * automatically capture data.
     */
    public fun isMemoryServiceEnabled(): Boolean

    /**
     * Returns true if SDK should automatically capture thermal status data
     */
    public fun isThermalStatusCaptureEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.PowerSaveModeService] should
     * automatically capture data.
     */
    public fun isPowerSaveModeServiceEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.NetworkConnectivityService] should
     * automatically capture data.
     */
    public fun isNetworkConnectivityServiceEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.anr.AnrService] should
     * automatically capture data.
     */
    public fun isAnrServiceEnabled(): Boolean

    /**
     * Control whether the Embrace SDK automatically attaches to the uncaught exception handler.
     */
    public fun isUncaughtExceptionHandlerEnabled(): Boolean

    /**
     * Whether Jetpack Compose click events should be captured
     */
    public fun isComposeOnClickEnabled(): Boolean

    /**
     * Whether embrace should attempt to overwrite other signal handlers
     */
    public fun isSigHandlerDetectionEnabled(): Boolean

    /**
     * Whether NDK error capture is enabled
     */
    public fun isNdkEnabled(): Boolean

    /**
     * Control whether we scan for and report app disk usage. This can be a costly operation
     * for apps with a lot of local files.
     */
    public fun isDiskUsageReportingEnabled(): Boolean
}
