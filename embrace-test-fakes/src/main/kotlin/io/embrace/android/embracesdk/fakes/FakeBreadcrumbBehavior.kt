package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior

class FakeBreadcrumbBehavior(
    var customBreadcrumbLimitImpl: Int = 100,
    var fragmentBreadcrumbLimitImpl: Int = 100,
    var tapBreadcrumbLimitImpl: Int = 100,
    var viewBreadcrumbLimitImpl: Int = 100,
    var webviewBreadcrumbLimitImpl: Int = 100,
    var tapCoordinateCaptureEnabled: Boolean = true,
    var automaticActivityCaptureEnabled: Boolean = true,
    var webViewBreadcrumbCaptureEnabled: Boolean = true,
    var queryParamCaptureEnabled: Boolean = true,
    var captureFcmPiiDataEnabled: Boolean = false
) : BreadcrumbBehavior {
    override fun getCustomBreadcrumbLimit(): Int = customBreadcrumbLimitImpl
    override fun getFragmentBreadcrumbLimit(): Int = fragmentBreadcrumbLimitImpl
    override fun getTapBreadcrumbLimit(): Int = tapBreadcrumbLimitImpl
    override fun getViewBreadcrumbLimit(): Int = viewBreadcrumbLimitImpl
    override fun getWebViewBreadcrumbLimit(): Int = webviewBreadcrumbLimitImpl
    override fun isTapCoordinateCaptureEnabled(): Boolean = tapCoordinateCaptureEnabled
    override fun isAutomaticActivityCaptureEnabled(): Boolean = automaticActivityCaptureEnabled
    override fun isWebViewBreadcrumbCaptureEnabled(): Boolean = webViewBreadcrumbCaptureEnabled
    override fun isQueryParamCaptureEnabled(): Boolean = queryParamCaptureEnabled
    override fun isCaptureFcmPiiDataEnabled(): Boolean = captureFcmPiiDataEnabled
}
