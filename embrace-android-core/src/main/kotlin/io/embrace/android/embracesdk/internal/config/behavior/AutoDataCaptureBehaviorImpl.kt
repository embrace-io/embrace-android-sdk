package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class AutoDataCaptureBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    override val remote: RemoteConfig?,
) : AutoDataCaptureBehavior {

    private companion object {
        const val THERMAL_STATUS_ENABLED_DEFAULT = true
        const val V2_STORAGE_ENABLED_DEFAULT = true
        const val USE_OKHTTP_DEFAULT = true
    }

    override val local = local.enabledFeatures

    @Suppress("DEPRECATION")
    override fun isMemoryWarningCaptureEnabled(): Boolean = local.isMemoryWarningCaptureEnabled()

    override fun isThermalStatusCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.dataConfig?.pctThermalStatusEnabled)
            ?: THERMAL_STATUS_ENABLED_DEFAULT
    }

    override fun isPowerSaveModeCaptureEnabled(): Boolean = local.isPowerSaveModeCaptureEnabled()
    override fun isNetworkConnectivityCaptureEnabled(): Boolean =
        local.isNetworkConnectivityCaptureEnabled()

    override fun isAnrCaptureEnabled(): Boolean = local.isAnrCaptureEnabled()
    override fun isJvmCrashCaptureEnabled(): Boolean = local.isJvmCrashCaptureEnabled()
    override fun isComposeClickCaptureEnabled(): Boolean =
        remote?.killSwitchConfig?.jetpackCompose ?: local.isComposeClickCaptureEnabled()

    override fun is3rdPartySigHandlerDetectionEnabled(): Boolean =
        remote?.killSwitchConfig?.sigHandlerDetection ?: local.is3rdPartySigHandlerDetectionEnabled()

    override fun isNativeCrashCaptureEnabled(): Boolean = local.isNativeCrashCaptureEnabled()
    override fun isDiskUsageCaptureEnabled(): Boolean = local.isDiskUsageCaptureEnabled()
    override fun isUiLoadPerfCaptureEnabled(): Boolean = local.isUiLoadPerfCaptureEnabled()

    private val v2StorageImpl by lazy {
        thresholdCheck.isBehaviorEnabled(remote?.killSwitchConfig?.v2StoragePct) ?: V2_STORAGE_ENABLED_DEFAULT
    }

    override fun isV2StorageEnabled(): Boolean = v2StorageImpl

    private val shouldUseOkHttpImpl by lazy {
        thresholdCheck.isBehaviorEnabled(remote?.killSwitchConfig?.useOkHttpPct) ?: USE_OKHTTP_DEFAULT
    }

    override fun shouldUseOkHttp(): Boolean = shouldUseOkHttpImpl
}
