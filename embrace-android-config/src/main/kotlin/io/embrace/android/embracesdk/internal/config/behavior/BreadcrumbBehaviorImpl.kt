package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior.Companion.DEFAULT_BREADCRUMB_LIMIT
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class BreadcrumbBehaviorImpl(
    local: InstrumentedConfig,
    private val remote: RemoteConfig?,
) : BreadcrumbBehavior {

    private val local: EnabledFeatureConfig = local.enabledFeatures

    override fun getCustomBreadcrumbLimit(): Int =
        remote?.uiConfig?.breadcrumbs ?: DEFAULT_BREADCRUMB_LIMIT

    override fun getFragmentBreadcrumbLimit(): Int =
        remote?.uiConfig?.fragments ?: DEFAULT_BREADCRUMB_LIMIT

    override fun getTapBreadcrumbLimit(): Int = remote?.uiConfig?.taps ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getWebViewBreadcrumbLimit(): Int =
        remote?.uiConfig?.webViews ?: DEFAULT_BREADCRUMB_LIMIT

    override fun isViewClickCoordinateCaptureEnabled(): Boolean =
        local.isViewClickCoordinateCaptureEnabled()

    override fun isActivityBreadcrumbCaptureEnabled(): Boolean =
        local.isActivityBreadcrumbCaptureEnabled()

    override fun isWebViewBreadcrumbCaptureEnabled(): Boolean =
        local.isWebViewBreadcrumbCaptureEnabled()

    override fun isWebViewBreadcrumbQueryParamCaptureEnabled(): Boolean =
        local.isWebViewBreadcrumbQueryParamCaptureEnabled()

    override fun getWebViewBreadcrumbFragmentCapture(): WebViewFragmentCapture =
        local.getWebViewBreadcrumbFragmentCapture()

    override fun isFcmPiiDataCaptureEnabled(): Boolean = local.isFcmPiiDataCaptureEnabled()
}
