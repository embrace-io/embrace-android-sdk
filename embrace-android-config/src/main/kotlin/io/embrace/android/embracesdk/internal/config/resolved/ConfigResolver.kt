package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

fun resolveConfig(local: InstrumentedConfig, remote: RemoteConfig?): EmbraceConfig = EmbraceConfig(
    breadcrumb = { resolveBreadcrumb(local, remote) },
)

fun resolveBreadcrumb(local: InstrumentedConfig, remote: RemoteConfig?): BreadcrumbConfig {
    val features = local.enabledFeatures
    val ui = remote?.uiConfig
    return BreadcrumbConfig(
        customLimit = { ui?.breadcrumbs },
        fragmentLimit = { ui?.fragments },
        tapLimit = { ui?.taps },
        webViewLimit = { ui?.webViews },
        captureViewClickCoordinates = features::isViewClickCoordinateCaptureEnabled,
        captureActivities = features::isActivityBreadcrumbCaptureEnabled,
        captureWebViews = features::isWebViewBreadcrumbCaptureEnabled,
        captureWebViewQueryParams = features::isWebViewBreadcrumbQueryParamCaptureEnabled,
        webViewFragmentCapture = features::getWebViewBreadcrumbFragmentCapture,
        captureFcmPiiData = features::isFcmPiiDataCaptureEnabled,
    )
}
