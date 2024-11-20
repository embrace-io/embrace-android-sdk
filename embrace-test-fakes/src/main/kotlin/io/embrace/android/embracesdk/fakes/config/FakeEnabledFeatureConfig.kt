package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig

@Suppress("DEPRECATION")
class FakeEnabledFeatureConfig(
    base: EnabledFeatureConfig = InstrumentedConfigImpl.enabledFeatures,
    private val unityAnrCapture: Boolean = base.isUnityAnrCaptureEnabled(),
    private val activityBreadcrumbCapture: Boolean = base.isActivityBreadcrumbCaptureEnabled(),
    private val composeClickCapture: Boolean = base.isComposeClickCaptureEnabled(),
    private val viewClickCoordCapture: Boolean = base.isViewClickCoordinateCaptureEnabled(),
    private val memoryWarningCapture: Boolean = base.isMemoryWarningCaptureEnabled(),
    private val powerSaveCapture: Boolean = base.isPowerSaveModeCaptureEnabled(),
    private val networkConnectivityCapture: Boolean = base.isNetworkConnectivityCaptureEnabled(),
    private val anrCapture: Boolean = base.isAnrCaptureEnabled(),
    private val diskUsageCapture: Boolean = base.isDiskUsageCaptureEnabled(),
    private val jvmCrashCapture: Boolean = base.isJvmCrashCaptureEnabled(),
    private val nativeCrashCapture: Boolean = base.isNativeCrashCaptureEnabled(),
    private val aeiCapture: Boolean = base.isAeiCaptureEnabled(),
    private val bgActivityCapture: Boolean = base.isBackgroundActivityCaptureEnabled(),
    private val webviewBreadcrumbCapture: Boolean = base.isWebViewBreadcrumbCaptureEnabled(),
    private val webviewQueryCapture: Boolean = base.isWebViewBreadcrumbQueryParamCaptureEnabled(),
    private val fcmPiiCapture: Boolean = base.isFcmPiiDataCaptureEnabled(),
    private val requestContentLengthCapture: Boolean = base.isRequestContentLengthCaptureEnabled(),
    private val httpUrlConnectionCapture: Boolean = base.isHttpUrlConnectionCaptureEnabled(),
    private val networkSpanForwarding: Boolean = base.isNetworkSpanForwardingEnabled(),
) : EnabledFeatureConfig {

    override fun isUnityAnrCaptureEnabled(): Boolean = unityAnrCapture
    override fun isActivityBreadcrumbCaptureEnabled(): Boolean = activityBreadcrumbCapture
    override fun isComposeClickCaptureEnabled(): Boolean = composeClickCapture
    override fun isViewClickCoordinateCaptureEnabled(): Boolean = viewClickCoordCapture

    @Deprecated("Will be removed in a future release.")
    override fun isMemoryWarningCaptureEnabled(): Boolean = memoryWarningCapture
    override fun isPowerSaveModeCaptureEnabled(): Boolean = powerSaveCapture
    override fun isNetworkConnectivityCaptureEnabled(): Boolean = networkConnectivityCapture
    override fun isAnrCaptureEnabled(): Boolean = anrCapture
    override fun isDiskUsageCaptureEnabled(): Boolean = diskUsageCapture
    override fun isJvmCrashCaptureEnabled(): Boolean = jvmCrashCapture
    override fun isNativeCrashCaptureEnabled(): Boolean = nativeCrashCapture
    override fun isAeiCaptureEnabled(): Boolean = aeiCapture
    override fun isBackgroundActivityCaptureEnabled(): Boolean = bgActivityCapture
    override fun isWebViewBreadcrumbCaptureEnabled(): Boolean = webviewBreadcrumbCapture
    override fun isWebViewBreadcrumbQueryParamCaptureEnabled(): Boolean = webviewQueryCapture
    override fun isFcmPiiDataCaptureEnabled(): Boolean = fcmPiiCapture
    override fun isRequestContentLengthCaptureEnabled(): Boolean = requestContentLengthCapture
    override fun isHttpUrlConnectionCaptureEnabled(): Boolean = httpUrlConnectionCapture
    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwarding
}
