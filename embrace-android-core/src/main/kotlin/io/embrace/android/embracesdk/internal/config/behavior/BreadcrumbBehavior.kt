package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface BreadcrumbBehavior : ConfigBehavior<EnabledFeatureConfig, RemoteConfig> {

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

    fun isFcmPiiDataCaptureEnabled(): Boolean
}
