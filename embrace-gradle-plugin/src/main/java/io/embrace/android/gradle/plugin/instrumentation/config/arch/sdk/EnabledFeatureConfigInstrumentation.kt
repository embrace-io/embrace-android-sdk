package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.boolMethod
import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

fun createEnabledFeatureConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    boolMethod("isNativeCrashCaptureEnabled") { cfg.embraceConfig?.ndkEnabled }
    with(cfg.embraceConfig?.sdkConfig ?: return@modelSdkConfigClass) {
        boolMethod("isUnityAnrCaptureEnabled") { anr?.captureUnityThread }
        boolMethod("isActivityBreadcrumbCaptureEnabled") { viewConfig?.enableAutomaticActivityCapture }
        boolMethod("isComposeClickCaptureEnabled") { composeConfig?.captureComposeOnClick }
        boolMethod("isViewClickCoordinateCaptureEnabled") { taps?.captureCoordinates }
        boolMethod("isMemoryWarningCaptureEnabled") { automaticDataCaptureConfig?.memoryServiceEnabled }
        boolMethod("isPowerSaveModeCaptureEnabled") { automaticDataCaptureConfig?.powerSaveModeServiceEnabled }
        boolMethod(
            "isNetworkConnectivityCaptureEnabled"
        ) { automaticDataCaptureConfig?.networkConnectivityServiceEnabled }
        boolMethod("isAnrCaptureEnabled") { automaticDataCaptureConfig?.anrServiceEnabled }
        boolMethod("isDiskUsageCaptureEnabled") { app?.reportDiskUsage }
        boolMethod("isJvmCrashCaptureEnabled") { crashHandler?.enabled }
        boolMethod("isAeiCaptureEnabled") { appExitInfoConfig?.aeiCaptureEnabled }
        boolMethod("is3rdPartySigHandlerDetectionEnabled") { sigHandlerDetection }
        boolMethod("isBackgroundActivityCaptureEnabled") { backgroundActivityConfig?.backgroundActivityCaptureEnabled }
        boolMethod("isWebViewBreadcrumbCaptureEnabled") { webViewConfig?.captureWebViews }
        boolMethod("isWebViewBreadcrumbQueryParamCaptureEnabled") { webViewConfig?.captureQueryParams }
        boolMethod("isFcmPiiDataCaptureEnabled") { captureFcmPiiData }
        boolMethod("isRequestContentLengthCaptureEnabled") { networking?.captureRequestContentLength }
        boolMethod("isHttpUrlConnectionCaptureEnabled") { networking?.enableNativeMonitoring }
        boolMethod("isNetworkSpanForwardingEnabled") { networking?.enableNetworkSpanForwarding }
        boolMethod("isUiLoadTracingEnabled") { automaticDataCaptureConfig?.uiLoadPerfTracingDisabled != true }
        boolMethod("isUiLoadTracingTraceAll") {
            if (automaticDataCaptureConfig != null) {
                automaticDataCaptureConfig.uiLoadPerfTracingSelectedOnly != true &&
                    automaticDataCaptureConfig.uiLoadPerfTracingDisabled != true
            } else {
                true
            }
        }
        boolMethod("isEndStartupWithAppReadyEnabled") { automaticDataCaptureConfig?.endStartupWithAppReadyEnabled }
    }
}
