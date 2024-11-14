package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeBreadcrumbBehavior(
    var customBreadcrumbLimitImpl: Int = 100,
    var fragmentBreadcrumbLimitImpl: Int = 100,
    var tapBreadcrumbLimitImpl: Int = 100,
    var webviewBreadcrumbLimitImpl: Int = 100,
    var tapCoordinateCaptureEnabled: Boolean = true,
    var automaticActivityCaptureEnabled: Boolean = true,
    var webViewBreadcrumbCaptureEnabled: Boolean = true,
    var queryParamCaptureEnabled: Boolean = true,
    var captureFcmPiiDataEnabled: Boolean = false,
) : BreadcrumbBehavior {

    override val local: EnabledFeatureConfig
        get() = throw UnsupportedOperationException()
    override val remote: RemoteConfig
        get() = throw UnsupportedOperationException()

    override fun getCustomBreadcrumbLimit(): Int = customBreadcrumbLimitImpl
    override fun getFragmentBreadcrumbLimit(): Int = fragmentBreadcrumbLimitImpl
    override fun getTapBreadcrumbLimit(): Int = tapBreadcrumbLimitImpl
    override fun getWebViewBreadcrumbLimit(): Int = webviewBreadcrumbLimitImpl
    override fun isViewClickCoordinateCaptureEnabled(): Boolean = tapCoordinateCaptureEnabled
    override fun isActivityBreadcrumbCaptureEnabled(): Boolean = automaticActivityCaptureEnabled
    override fun isWebViewBreadcrumbCaptureEnabled(): Boolean = webViewBreadcrumbCaptureEnabled
    override fun isWebViewBreadcrumbQueryParamCaptureEnabled(): Boolean = queryParamCaptureEnabled
    override fun isFcmPiiDataCaptureEnabled(): Boolean = captureFcmPiiDataEnabled
}
