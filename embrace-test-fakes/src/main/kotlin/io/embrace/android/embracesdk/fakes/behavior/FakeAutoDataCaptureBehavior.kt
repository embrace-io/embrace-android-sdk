package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeAutoDataCaptureBehavior(
    private val memoryServiceEnabled: Boolean = true,
    private val thermalStatusCaptureEnabled: Boolean = true,
    private val powerSaveModeServiceEnabled: Boolean = true,
    private val networkConnectivityServiceEnabled: Boolean = true,
    private val anrServiceEnabled: Boolean = true,
    private val uncaughtExceptionHandlerEnabled: Boolean = true,
    private val composeOnClickEnabled: Boolean = true,
    private val ndkEnabled: Boolean = false,
    private val diskUsageReportingEnabled: Boolean = true,
    private val v2StorageEnabled: Boolean = true,
    private val useOkhttp: Boolean = true,
) : AutoDataCaptureBehavior {

    override val local: EnabledFeatureConfig
        get() = throw UnsupportedOperationException()
    override val remote: RemoteConfig
        get() = throw UnsupportedOperationException()

    override fun isMemoryWarningCaptureEnabled(): Boolean = memoryServiceEnabled
    override fun isThermalStatusCaptureEnabled(): Boolean = thermalStatusCaptureEnabled
    override fun isPowerSaveModeCaptureEnabled(): Boolean = powerSaveModeServiceEnabled
    override fun isNetworkConnectivityCaptureEnabled(): Boolean = networkConnectivityServiceEnabled
    override fun isAnrCaptureEnabled(): Boolean = anrServiceEnabled
    override fun isJvmCrashCaptureEnabled(): Boolean = uncaughtExceptionHandlerEnabled
    override fun isComposeClickCaptureEnabled(): Boolean = composeOnClickEnabled
    override fun isNativeCrashCaptureEnabled(): Boolean = ndkEnabled
    override fun isDiskUsageCaptureEnabled(): Boolean = diskUsageReportingEnabled
    override fun isV2StorageEnabled(): Boolean = v2StorageEnabled
    override fun shouldUseOkHttp(): Boolean = useOkhttp
}
