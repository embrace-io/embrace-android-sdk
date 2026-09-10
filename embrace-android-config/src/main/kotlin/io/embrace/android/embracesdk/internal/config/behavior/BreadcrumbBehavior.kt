package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture

interface BreadcrumbBehavior {

    fun getCustomBreadcrumbLimit(): Int
    fun getFragmentBreadcrumbLimit(): Int
    fun getTapBreadcrumbLimit(): Int
    fun getWebViewBreadcrumbLimit(): Int

    /**
     * Controls whether tap coordinates are captured in breadcrumbs
     */
    fun isViewClickCoordinateCaptureEnabled(): Boolean

    /**
     * Controls whether activity lifecycle changes are captured in breadcrumbs
     */
    fun isActivityBreadcrumbCaptureEnabled(): Boolean

    /**
     * Controls whether webviews are captured.
     */
    fun isWebViewBreadcrumbCaptureEnabled(): Boolean

    /**
     * Control whether query params for webviews are captured.
     */
    fun isWebViewBreadcrumbQueryParamCaptureEnabled(): Boolean

    /**
     * Controls how the URL fragment for webviews is captured. A fragment carries OAuth
     * implicit-grant 'access_token' and OpenID Connect 'id_token' values by specification, and it
     * also carries the hash route the user was on, so both keeping and dropping it lose something.
     */
    fun getWebViewBreadcrumbFragmentCapture(): WebViewFragmentCapture

    fun isFcmPiiDataCaptureEnabled(): Boolean

    companion object {

        /**
         * The default breadcrumbs capture limit.
         */
        const val DEFAULT_BREADCRUMB_LIMIT: Int = 100
    }
}
