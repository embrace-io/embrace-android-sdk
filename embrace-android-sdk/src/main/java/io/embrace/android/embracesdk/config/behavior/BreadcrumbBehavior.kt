package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface BreadcrumbBehavior {

    public fun getCustomBreadcrumbLimit(): Int
    public fun getFragmentBreadcrumbLimit(): Int
    public fun getTapBreadcrumbLimit(): Int
    public fun getViewBreadcrumbLimit(): Int
    public fun getWebViewBreadcrumbLimit(): Int

    /**
     * Controls whether tap coordinates are captured in breadcrumbs
     */
    public fun isTapCoordinateCaptureEnabled(): Boolean

    /**
     * Controls whether activity lifecycle changes are captured in breadcrumbs
     */
    public fun isAutomaticActivityCaptureEnabled(): Boolean

    /**
     * Controls whether webviews are captured.
     */
    public fun isWebViewBreadcrumbCaptureEnabled(): Boolean

    /**
     * Control whether query params for webviews are captured.
     */
    public fun isQueryParamCaptureEnabled(): Boolean

    public fun isCaptureFcmPiiDataEnabled(): Boolean
}
