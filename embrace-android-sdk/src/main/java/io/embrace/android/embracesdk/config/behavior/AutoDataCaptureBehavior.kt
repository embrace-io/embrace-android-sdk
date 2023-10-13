package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.ApkToolsConfig

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
internal class AutoDataCaptureBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: () -> LocalConfig?,
    remoteSupplier: () -> RemoteConfig?
) : MergedConfigBehavior<LocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {
        const val MEMORY_SERVICE_ENABLED_DEFAULT = true
        const val POWER_SAVE_MODE_SERVICE_ENABLED_DEFAULT = true
        const val NETWORK_CONNECTIVITY_SERVICE_ENABLED_DEFAULT = true
        const val ANR_SERVICE_ENABLED_DEFAULT = true
        const val CRASH_HANDLER_ENABLED_DEFAULT = true
        const val CAPTURE_COMPOSE_ONCLICK_DEFAULT = false
        const val REPORT_DISK_USAGE_DEFAULT = true
    }

    /**
     * Returns true if [io.embrace.android.embracesdk.MemoryService] should
     * automatically capture data.
     */
    fun isMemoryServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.memoryServiceEnabled
            ?: MEMORY_SERVICE_ENABLED_DEFAULT
    }

    /**
     * Returns true if [io.embrace.android.embracesdk.PowerSaveModeService] should
     * automatically capture data.
     */
    fun isPowerSaveModeServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.powerSaveModeServiceEnabled
            ?: POWER_SAVE_MODE_SERVICE_ENABLED_DEFAULT
    }

    /**
     * Returns true if [io.embrace.android.embracesdk.NetworkConnectivityService] should
     * automatically capture data.
     */
    fun isNetworkConnectivityServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.networkConnectivityServiceEnabled
            ?: NETWORK_CONNECTIVITY_SERVICE_ENABLED_DEFAULT
    }

    /**
     * Returns true if [io.embrace.android.embracesdk.anr.AnrService] should
     * automatically capture data.
     */
    fun isAnrServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.anrServiceEnabled
            ?: ANR_SERVICE_ENABLED_DEFAULT
    }

    /**
     * Control whether the Embrace SDK automatically attaches to the uncaught exception handler.
     */
    fun isUncaughtExceptionHandlerEnabled(): Boolean =
        local?.sdkConfig?.crashHandler?.enabled ?: CRASH_HANDLER_ENABLED_DEFAULT

    /**
     * Whether Jetpack Compose click events should be captured
     */
    fun isComposeOnClickEnabled(): Boolean {
        return when (remote?.killSwitchConfig?.jetpackCompose) {
            null, true -> {
                // no remote: use local
                // remote is true: it can be explicitly disabled locally
                local?.sdkConfig?.composeConfig?.captureComposeOnClick ?: CAPTURE_COMPOSE_ONCLICK_DEFAULT
            }
            false -> {
                // remote is false: the killswitch ignores local
                false
            }
        }
    }

    /**
     * Whether embrace should attempt to overwrite other signal handlers
     */
    fun isSigHandlerDetectionEnabled(): Boolean {
        return remote?.killSwitchConfig?.sigHandlerDetection
            ?: local?.sdkConfig?.sigHandlerDetection ?: true
    }

    /**
     * Whether NDK error capture is enabled
     */
    fun isNdkEnabled(): Boolean = local?.ndkEnabled ?: false && !ApkToolsConfig.IS_NDK_DISABLED

    /**
     * Control whether we scan for and report app disk usage. This can be a costly operation
     * for apps with a lot of local files.
     */
    fun isDiskUsageReportingEnabled(): Boolean =
        local?.sdkConfig?.app?.reportDiskUsage ?: REPORT_DISK_USAGE_DEFAULT
}
