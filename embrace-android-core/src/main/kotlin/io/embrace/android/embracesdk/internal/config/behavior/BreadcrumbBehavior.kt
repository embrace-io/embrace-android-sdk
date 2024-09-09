package io.embrace.android.embracesdk.internal.config.behavior

interface BreadcrumbBehavior {

    fun getCustomBreadcrumbLimit(): Int
    fun getFragmentBreadcrumbLimit(): Int
    fun getTapBreadcrumbLimit(): Int
    fun getViewBreadcrumbLimit(): Int
    fun getWebViewBreadcrumbLimit(): Int

    /**
     * Controls whether tap coordinates are captured in breadcrumbs
     */
    fun isTapCoordinateCaptureEnabled(): Boolean

    /**
     * Controls whether activity lifecycle changes are captured in breadcrumbs
     */
    fun isAutomaticActivityCaptureEnabled(): Boolean

    /**
     * Controls whether webviews are captured.
     */
    fun isWebViewBreadcrumbCaptureEnabled(): Boolean

    /**
     * Control whether query params for webviews are captured.
     */
    fun isQueryParamCaptureEnabled(): Boolean

    fun isCaptureFcmPiiDataEnabled(): Boolean
}
