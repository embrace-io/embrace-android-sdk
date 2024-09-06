package io.embrace.android.embracesdk.internal.config.behavior

interface AutoDataCaptureBehavior {

    /**
     * Returns true if [io.embrace.android.embracesdk.MemoryService] should
     * automatically capture data.
     */
    fun isMemoryServiceEnabled(): Boolean

    /**
     * Returns true if SDK should automatically capture thermal status data
     */
    fun isThermalStatusCaptureEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.PowerSaveModeService] should
     * automatically capture data.
     */
    fun isPowerSaveModeServiceEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.NetworkConnectivityService] should
     * automatically capture data.
     */
    fun isNetworkConnectivityServiceEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.anr.AnrService] should
     * automatically capture data.
     */
    fun isAnrServiceEnabled(): Boolean

    /**
     * Control whether the Embrace SDK automatically attaches to the uncaught exception handler.
     */
    fun isUncaughtExceptionHandlerEnabled(): Boolean

    /**
     * Whether Jetpack Compose click events should be captured
     */
    fun isComposeOnClickEnabled(): Boolean

    /**
     * Whether embrace should attempt to overwrite other signal handlers
     */
    fun isSigHandlerDetectionEnabled(): Boolean

    /**
     * Whether NDK error capture is enabled
     */
    fun isNdkEnabled(): Boolean

    /**
     * Control whether we scan for and report app disk usage. This can be a costly operation
     * for apps with a lot of local files.
     */
    fun isDiskUsageReportingEnabled(): Boolean
}
