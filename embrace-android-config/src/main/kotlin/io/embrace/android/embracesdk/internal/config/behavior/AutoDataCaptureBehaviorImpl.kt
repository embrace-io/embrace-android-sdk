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
    private val remote: RemoteConfig?,
) : AutoDataCaptureBehavior {

    private companion object {
        const val THERMAL_STATUS_ENABLED_DEFAULT = true
        const val UI_LOAD_REMOTE_ENABLED_DEFAULT = true
    }

    private val local = local.enabledFeatures

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
    override fun isUiLoadTracingEnabled(): Boolean = local.isUiLoadTracingEnabled() && uiLoadEnabledRemotely()
    override fun isUiLoadTracingTraceAll(): Boolean = local.isUiLoadTracingTraceAll() && uiLoadEnabledRemotely()

    private fun uiLoadEnabledRemotely(): Boolean =
        remote?.uiLoadInstrumentationEnabled ?: UI_LOAD_REMOTE_ENABLED_DEFAULT

    override fun isEndStartupWithAppReadyEnabled(): Boolean = local.isEndStartupWithAppReadyEnabled()
}
