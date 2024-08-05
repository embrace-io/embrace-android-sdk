package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
public class BreadcrumbBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<SdkLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : BreadcrumbBehavior, MergedConfigBehavior<SdkLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    private companion object {

        /**
         * The default breadcrumbs capture limit.
         */
        const val DEFAULT_BREADCRUMB_LIMIT = 100
        const val CAPTURE_TAP_COORDINATES_DEFAULT = true
        const val ENABLE_AUTOMATIC_ACTIVITY_CAPTURE_DEFAULT = true
        const val WEB_VIEW_CAPTURE_DEFAULT = true
        const val WEB_VIEW_QUERY_PARAMS_CAPTURE_DEFAULT = true
    }

    override fun getCustomBreadcrumbLimit(): Int = remote?.uiConfig?.breadcrumbs ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getFragmentBreadcrumbLimit(): Int = remote?.uiConfig?.fragments ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getTapBreadcrumbLimit(): Int = remote?.uiConfig?.taps ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getViewBreadcrumbLimit(): Int = remote?.uiConfig?.views ?: DEFAULT_BREADCRUMB_LIMIT
    override fun getWebViewBreadcrumbLimit(): Int = remote?.uiConfig?.webViews ?: DEFAULT_BREADCRUMB_LIMIT

    override fun isTapCoordinateCaptureEnabled(): Boolean =
        local?.taps?.captureCoordinates ?: CAPTURE_TAP_COORDINATES_DEFAULT

    override fun isAutomaticActivityCaptureEnabled(): Boolean =
        local?.viewConfig?.enableAutomaticActivityCapture
            ?: ENABLE_AUTOMATIC_ACTIVITY_CAPTURE_DEFAULT

    override fun isWebViewBreadcrumbCaptureEnabled(): Boolean =
        local?.webViewConfig?.captureWebViews ?: WEB_VIEW_CAPTURE_DEFAULT

    override fun isQueryParamCaptureEnabled(): Boolean =
        local?.webViewConfig?.captureQueryParams ?: WEB_VIEW_QUERY_PARAMS_CAPTURE_DEFAULT

    override fun isCaptureFcmPiiDataEnabled(): Boolean {
        return try {
            local?.captureFcmPiiData ?: false
        } catch (ex: Exception) {
            false
        }
    }
}
