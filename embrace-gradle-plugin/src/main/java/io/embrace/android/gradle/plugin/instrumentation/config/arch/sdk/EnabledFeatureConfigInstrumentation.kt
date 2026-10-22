package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.boolMethod
import io.embrace.android.gradle.plugin.instrumentation.config.arch.enumMethod
import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

private const val WEB_VIEW_FRAGMENT_CAPTURE_CLASS =
    "io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture"

fun createEnabledFeatureConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    boolMethod("isNativeCrashCaptureEnabled") { cfg.embraceConfig?.ndkEnabled }
    with(cfg.embraceConfig?.sdkConfig ?: return@modelSdkConfigClass) {
        boolMethod("isActivityBreadcrumbCaptureEnabled") { viewConfig?.enableAutomaticActivityCapture }
        boolMethod("isComposeClickCaptureEnabled") { composeConfig?.captureComposeOnClick }
        boolMethod("isViewClickCoordinateCaptureEnabled") { taps?.captureCoordinates }
        boolMethod("isPowerSaveModeCaptureEnabled") { automaticDataCaptureConfig?.powerSaveModeServiceEnabled }
        boolMethod(
            "isNetworkConnectivityCaptureEnabled",
        ) { automaticDataCaptureConfig?.networkConnectivityServiceEnabled }
        boolMethod("isThreadBlockageCaptureEnabled") { automaticDataCaptureConfig?.threadBlockageServiceEnabled }
        boolMethod("isDiskUsageCaptureEnabled") { app?.reportDiskUsage }
        boolMethod("isJvmCrashCaptureEnabled") { crashHandler?.enabled }
        boolMethod("isAeiCaptureEnabled") { appExitInfoConfig?.aeiCaptureEnabled }
        boolMethod("is3rdPartySigHandlerDetectionEnabled") { sigHandlerDetection }
        boolMethod("isBackgroundActivityCaptureEnabled") { backgroundActivityConfig?.backgroundActivityCaptureEnabled }
        boolMethod("isWebViewBreadcrumbCaptureEnabled") { webViewConfig?.captureWebViews }
        boolMethod("isWebViewBreadcrumbQueryParamCaptureEnabled") { webViewConfig?.captureQueryParams }
        enumMethod("getWebViewBreadcrumbFragmentCapture", WEB_VIEW_FRAGMENT_CAPTURE_CLASS) {
            webViewConfig?.fragmentCapture?.name
        }
        boolMethod("isFcmPiiDataCaptureEnabled") { captureFcmPiiData }
        boolMethod("isRequestContentLengthCaptureEnabled") { networking?.captureRequestContentLength }
        boolMethod("isOkHttpResponseBodySizeCaptureEnabled") { networking?.captureOkHttpResponseBodySize }
        boolMethod("isHttpUrlConnectionCaptureEnabled") { networking?.enableNativeMonitoring }
        boolMethod("isHucLiteInstrumentationEnabled") { networking?.enableHucLiteInstrumentation }
        boolMethod("isNetworkSpanForwardingEnabled") { networking?.enableNetworkSpanForwarding }
        boolMethod("isTraceparentInjectionEnabled") { networking?.enableTraceparentInjection }
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
        boolMethod("isOtelKotlinSdkEnabled") { otel?.otelKotlinSdkEnabled }
        boolMethod("isActivityProcessLifecycleTrackerEnabled") {
            automaticDataCaptureConfig?.activityProcessLifecycleTrackerEnabled
        }
    }
}
