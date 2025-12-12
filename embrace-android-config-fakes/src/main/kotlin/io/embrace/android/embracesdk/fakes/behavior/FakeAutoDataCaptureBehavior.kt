package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior

class FakeAutoDataCaptureBehavior(
    private val thermalStatusCaptureEnabled: Boolean = true,
    private val powerSaveModeServiceEnabled: Boolean = true,
    private val networkConnectivityServiceEnabled: Boolean = true,
    private val threadBlockageServiceEnabled: Boolean = true,
    private val uncaughtExceptionHandlerEnabled: Boolean = true,
    private val composeOnClickEnabled: Boolean = true,
    private val sigHandlerDetectionEnabled: Boolean = true,
    private val ndkEnabled: Boolean = false,
    private val diskUsageReportingEnabled: Boolean = true,
    private val uiLoadTracingEnabled: Boolean = true,
    private val uiLoadTracingTraceAll: Boolean = true,
    private val endStartupWithAppReady: Boolean = false,
    private val enableStateCapture: Boolean = false,
) : AutoDataCaptureBehavior {

    override fun isThermalStatusCaptureEnabled(): Boolean = thermalStatusCaptureEnabled
    override fun isPowerSaveModeCaptureEnabled(): Boolean = powerSaveModeServiceEnabled
    override fun isNetworkConnectivityCaptureEnabled(): Boolean = networkConnectivityServiceEnabled
    override fun isThreadBlockageCaptureEnabled(): Boolean = threadBlockageServiceEnabled
    override fun isJvmCrashCaptureEnabled(): Boolean = uncaughtExceptionHandlerEnabled
    override fun isComposeClickCaptureEnabled(): Boolean = composeOnClickEnabled
    override fun is3rdPartySigHandlerDetectionEnabled(): Boolean = sigHandlerDetectionEnabled
    override fun isNativeCrashCaptureEnabled(): Boolean = ndkEnabled
    override fun isDiskUsageCaptureEnabled(): Boolean = diskUsageReportingEnabled
    override fun isUiLoadTracingEnabled(): Boolean = uiLoadTracingEnabled
    override fun isUiLoadTracingTraceAll(): Boolean = uiLoadTracingTraceAll
    override fun isEndStartupWithAppReadyEnabled(): Boolean = endStartupWithAppReady
    override fun isStateCaptureEnabled(): Boolean = enableStateCapture
}
