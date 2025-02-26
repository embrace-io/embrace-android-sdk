package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface AutoDataCaptureBehavior : ConfigBehavior<EnabledFeatureConfig, RemoteConfig> {

    /**
     * Returns true if [io.embrace.android.embracesdk.MemoryService] should
     * automatically capture data.
     */
    fun isMemoryWarningCaptureEnabled(): Boolean

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
     * Returns true if [io.embrace.android.embracesdk.anr.AnrService] should
     * automatically capture data.
     */
    fun isAnrCaptureEnabled(): Boolean

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
     * Gates whether the SDK should use the v2 storage implementation or the legacy one.
     */
    fun isV2StorageEnabled(): Boolean

    /**
     * Whether the SDK is configured to capture traces for the performance of the opening of Activities.
     */
    fun isUiLoadTracingEnabled(): Boolean

    /**
     * Whether the SDK is configured to capture traces for the performance of the opening of all Activities by default.
     */
    fun isUiLoadTracingTraceAll(): Boolean

    /**
     * Gates whether the SDK should use OkHttp or fallback to UrlConnection.
     */
    fun shouldUseOkHttp(): Boolean

    /**
     * Whether the app startup trace will be waiting for a call to Embrace.appReady() to signal completion
     */
    fun isManualAppStartupCompletionEnabled(): Boolean
}
