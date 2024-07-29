package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
internal class AutoDataCaptureBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<LocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : AutoDataCaptureBehavior, MergedConfigBehavior<LocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {
        const val MEMORY_SERVICE_ENABLED_DEFAULT = true
        const val THERMAL_STATUS_ENABLED_DEFAULT = true
        const val POWER_SAVE_MODE_SERVICE_ENABLED_DEFAULT = true
        const val NETWORK_CONNECTIVITY_SERVICE_ENABLED_DEFAULT = true
        const val ANR_SERVICE_ENABLED_DEFAULT = true
        const val CRASH_HANDLER_ENABLED_DEFAULT = true
        const val CAPTURE_COMPOSE_ONCLICK_DEFAULT = false
        const val REPORT_DISK_USAGE_DEFAULT = true
    }

    override fun isMemoryServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.memoryServiceEnabled
            ?: MEMORY_SERVICE_ENABLED_DEFAULT
    }

    override fun isThermalStatusCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.dataConfig?.pctThermalStatusEnabled)
            ?: THERMAL_STATUS_ENABLED_DEFAULT
    }

    override fun isPowerSaveModeServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.powerSaveModeServiceEnabled
            ?: POWER_SAVE_MODE_SERVICE_ENABLED_DEFAULT
    }

    override fun isNetworkConnectivityServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.networkConnectivityServiceEnabled
            ?: NETWORK_CONNECTIVITY_SERVICE_ENABLED_DEFAULT
    }

    override fun isAnrServiceEnabled(): Boolean {
        return local?.sdkConfig?.automaticDataCaptureConfig?.anrServiceEnabled
            ?: ANR_SERVICE_ENABLED_DEFAULT
    }

    override fun isUncaughtExceptionHandlerEnabled(): Boolean =
        local?.sdkConfig?.crashHandler?.enabled ?: CRASH_HANDLER_ENABLED_DEFAULT

    override fun isComposeOnClickEnabled(): Boolean {
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

    override fun isSigHandlerDetectionEnabled(): Boolean {
        return remote?.killSwitchConfig?.sigHandlerDetection
            ?: local?.sdkConfig?.sigHandlerDetection ?: true
    }

    override fun isNdkEnabled(): Boolean = local?.ndkEnabled ?: false

    override fun isDiskUsageReportingEnabled(): Boolean =
        local?.sdkConfig?.app?.reportDiskUsage ?: REPORT_DISK_USAGE_DEFAULT
}
