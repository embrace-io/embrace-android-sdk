package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class BreadcrumbBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?>
) : BreadcrumbBehavior, MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {

    private companion object {

        /**
         * The default breadcrumbs capture limit.
         */
        const val DEFAULT_BREADCRUMB_LIMIT = 100
    }

    private val cfg = InstrumentedConfig.enabledFeatures

    override fun getCustomBreadcrumbLimit(): Int =
        remote?.uiConfig?.breadcrumbs ?: DEFAULT_BREADCRUMB_LIMIT

    override fun getFragmentBreadcrumbLimit(): Int =
        remote?.uiConfig?.fragments ?: DEFAULT_BREADCRUMB_LIMIT

    override fun getTapBreadcrumbLimit(): Int = remote?.uiConfig?.taps ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getViewBreadcrumbLimit(): Int = remote?.uiConfig?.views ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getWebViewBreadcrumbLimit(): Int =
        remote?.uiConfig?.webViews ?: DEFAULT_BREADCRUMB_LIMIT

    override fun isViewClickCoordinateCaptureEnabled(): Boolean =
        cfg.isViewClickCoordinateCaptureEnabled()

    override fun isActivityBreadcrumbCaptureEnabled(): Boolean =
        cfg.isActivityBreadcrumbCaptureEnabled()

    override fun isWebViewBreadcrumbCaptureEnabled(): Boolean =
        cfg.isWebViewBreadcrumbCaptureEnabled()

    override fun isWebViewBreadcrumbQueryParamCaptureEnabled(): Boolean =
        cfg.isWebViewBreadcrumbQueryParamCaptureEnabled()

    override fun isFcmPiiDataCaptureEnabled(): Boolean = cfg.isFcmPiiDataCaptureEnabled()
}
