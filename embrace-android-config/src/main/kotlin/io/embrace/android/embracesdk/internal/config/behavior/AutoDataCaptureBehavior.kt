package io.embrace.android.embracesdk.internal.config.behavior

interface AutoDataCaptureBehavior {

    /**
     * Returns true if SDK should automatically capture thermal status data
     */
    fun isThermalStatusCaptureEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.PowerSaveModeService] should
     * automatically capture data.
     */
    fun isPowerSaveModeCaptureEnabled(): Boolean

    /**
     * Returns true if [io.embrace.android.embracesdk.NetworkConnectivityService] should
     * automatically capture data.
     */
    fun isNetworkConnectivityCaptureEnabled(): Boolean

    /**
     * Returns true if thread blockages should be captured.
     */
    fun isThreadBlockageCaptureEnabled(): Boolean

    /**
     * Control whether the Embrace SDK automatically attaches to the uncaught exception handler.
     */
    fun isJvmCrashCaptureEnabled(): Boolean

    /**
     * Whether Jetpack Compose click events should be captured
     */
    fun isComposeClickCaptureEnabled(): Boolean

    /**
     * Whether embrace should attempt to overwrite other signal handlers
     */
    fun is3rdPartySigHandlerDetectionEnabled(): Boolean

    /**
     * Whether NDK error capture is enabled
     */
    fun isNativeCrashCaptureEnabled(): Boolean

    /**
     * Control whether we scan for and report app disk usage. This can be a costly operation
     * for apps with a lot of local files.
     */
    fun isDiskUsageCaptureEnabled(): Boolean

    /**
     * Whether the SDK is configured to capture traces for the performance of the opening of Activities.
     */
    fun isUiLoadTracingEnabled(): Boolean

    /**
     * Whether the SDK is configured to capture traces for the performance of the opening of all Activities by default.
     */
    fun isUiLoadTracingTraceAll(): Boolean

    /**
     * Whether the app startup trace will be waiting for a call to Embrace.appReady() to signal completion
     */
    fun isEndStartupWithAppReadyEnabled(): Boolean
}
