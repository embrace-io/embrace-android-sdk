package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
internal class BreadcrumbBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<SdkLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : MergedConfigBehavior<SdkLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {

        /**
         * The default breadcrumbs capture limit.
         */
        const val DEFAULT_BREADCRUMB_LIMIT = 100
        const val CAPTURE_TAP_COORDINATES_DEFAULT = true
        const val ENABLE_AUTOMATIC_ACTIVITY_CAPTURE_DEFAULT = true
        const val WEB_VIEW_CAPTURE_DEFAULT = true
        const val WEB_VIEW_QUERY_PARAMS_CAPTURE_DEFAULT = true
    }

    fun getCustomBreadcrumbLimit(): Int = remote?.uiConfig?.breadcrumbs ?: DEFAULT_BREADCRUMB_LIMIT
    fun getFragmentBreadcrumbLimit(): Int = remote?.uiConfig?.fragments ?: DEFAULT_BREADCRUMB_LIMIT
    fun getTapBreadcrumbLimit(): Int = remote?.uiConfig?.taps ?: DEFAULT_BREADCRUMB_LIMIT
    fun getViewBreadcrumbLimit(): Int = remote?.uiConfig?.views ?: DEFAULT_BREADCRUMB_LIMIT
    fun getWebViewBreadcrumbLimit(): Int = remote?.uiConfig?.webViews ?: DEFAULT_BREADCRUMB_LIMIT

    /**
     * Controls whether tap coordinates are captured in breadcrumbs
     */
    fun isTapCoordinateCaptureEnabled(): Boolean =
        local?.taps?.captureCoordinates ?: CAPTURE_TAP_COORDINATES_DEFAULT

    /**
     * Controls whether activity lifecycle changes are captured in breadcrumbs
     */
    fun isActivityBreadcrumbCaptureEnabled() =
        local?.viewConfig?.enableAutomaticActivityCapture
            ?: ENABLE_AUTOMATIC_ACTIVITY_CAPTURE_DEFAULT

    /**
     * Controls whether webviews are captured.
     */
    fun isWebViewBreadcrumbCaptureEnabled(): Boolean =
        local?.webViewConfig?.captureWebViews ?: WEB_VIEW_CAPTURE_DEFAULT

    /**
     * Control whether query params for webviews are captured.
     */
    fun isQueryParamCaptureEnabled(): Boolean =
        local?.webViewConfig?.captureQueryParams ?: WEB_VIEW_QUERY_PARAMS_CAPTURE_DEFAULT

    fun isCaptureFcmPiiDataEnabled(): Boolean = local?.captureFcmPiiData ?: false
}
