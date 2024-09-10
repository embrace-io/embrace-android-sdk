package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior

class FakeAutoDataCaptureBehavior(
    private val memoryServiceEnabled: Boolean = true,
    private val thermalStatusCaptureEnabled: Boolean = true,
    private val powerSaveModeServiceEnabled: Boolean = true,
    private val networkConnectivityServiceEnabled: Boolean = true,
    private val anrServiceEnabled: Boolean = true,
    private val uncaughtExceptionHandlerEnabled: Boolean = true,
    private val composeOnClickEnabled: Boolean = true,
    private val sigHandlerDetectionEnabled: Boolean = true,
    private val ndkEnabled: Boolean = false,
    private val diskUsageReportingEnabled: Boolean = true
) : AutoDataCaptureBehavior {

    override fun isMemoryServiceEnabled(): Boolean = memoryServiceEnabled
    override fun isThermalStatusCaptureEnabled(): Boolean = thermalStatusCaptureEnabled
    override fun isPowerSaveModeServiceEnabled(): Boolean = powerSaveModeServiceEnabled
    override fun isNetworkConnectivityServiceEnabled(): Boolean = networkConnectivityServiceEnabled
    override fun isAnrServiceEnabled(): Boolean = anrServiceEnabled
    override fun isUncaughtExceptionHandlerEnabled(): Boolean = uncaughtExceptionHandlerEnabled
    override fun isComposeOnClickEnabled(): Boolean = composeOnClickEnabled
    override fun isSigHandlerDetectionEnabled(): Boolean = sigHandlerDetectionEnabled
    override fun isNdkEnabled(): Boolean = ndkEnabled
    override fun isDiskUsageReportingEnabled(): Boolean = diskUsageReportingEnabled
}
