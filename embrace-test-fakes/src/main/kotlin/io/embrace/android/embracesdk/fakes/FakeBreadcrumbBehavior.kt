package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior

public class FakeBreadcrumbBehavior(
    public var customBreadcrumbLimitImpl: Int = 100,
    public var fragmentBreadcrumbLimitImpl: Int = 100,
    public var tapBreadcrumbLimitImpl: Int = 100,
    public var viewBreadcrumbLimitImpl: Int = 100,
    public var webviewBreadcrumbLimitImpl: Int = 100,
    public var tapCoordinateCaptureEnabled: Boolean = true,
    public var automaticActivityCaptureEnabled: Boolean = true,
    public var webViewBreadcrumbCaptureEnabled: Boolean = true,
    public var queryParamCaptureEnabled: Boolean = true,
    public var captureFcmPiiDataEnabled: Boolean = false
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
