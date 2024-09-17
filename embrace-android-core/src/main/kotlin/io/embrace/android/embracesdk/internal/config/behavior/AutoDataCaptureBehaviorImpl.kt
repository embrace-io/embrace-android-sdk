package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class AutoDataCaptureBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?>
) : AutoDataCaptureBehavior, MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {

    private companion object {
        const val THERMAL_STATUS_ENABLED_DEFAULT = true
    }

    private val cfg = InstrumentedConfig.enabledFeatures

    override fun isMemoryWarningCaptureEnabled(): Boolean = cfg.isMemoryWarningCaptureEnabled()

    override fun isThermalStatusCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.dataConfig?.pctThermalStatusEnabled)
            ?: THERMAL_STATUS_ENABLED_DEFAULT
    }

    override fun isPowerSaveModeCaptureEnabled(): Boolean = cfg.isPowerSaveModeCaptureEnabled()
    override fun isNetworkConnectivityCaptureEnabled(): Boolean =
        cfg.isNetworkConnectivityCaptureEnabled()

    override fun isAnrCaptureEnabled(): Boolean = cfg.isAnrCaptureEnabled()
    override fun isJvmCrashCaptureEnabled(): Boolean = cfg.isJvmCrashCaptureEnabled()
    override fun isComposeClickCaptureEnabled(): Boolean =
        remote?.killSwitchConfig?.jetpackCompose ?: cfg.isComposeClickCaptureEnabled()

    override fun is3rdPartySigHandlerDetectionEnabled(): Boolean =
        remote?.killSwitchConfig?.sigHandlerDetection ?: cfg.is3rdPartySigHandlerDetectionEnabled()

    override fun isNativeCrashCaptureEnabled(): Boolean = cfg.isNativeCrashCaptureEnabled()
    override fun isDiskUsageCaptureEnabled(): Boolean = cfg.isDiskUsageCaptureEnabled()
}
