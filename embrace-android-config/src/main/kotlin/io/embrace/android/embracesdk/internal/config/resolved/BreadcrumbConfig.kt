package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture

/**
 * Resolved breadcrumb config. A provider returning null means the default is used.
 */
class BreadcrumbConfig(
    customLimit: () -> Int? = { null },
    fragmentLimit: () -> Int? = { null },
    tapLimit: () -> Int? = { null },
    webViewLimit: () -> Int? = { null },
    captureViewClickCoordinates: () -> Boolean? = { null },
    captureActivities: () -> Boolean? = { null },
    captureWebViews: () -> Boolean? = { null },
    captureWebViewQueryParams: () -> Boolean? = { null },
    webViewFragmentCapture: () -> WebViewFragmentCapture? = { null },
    captureFcmPiiData: () -> Boolean? = { null },
) {
    val customLimit: Int by lazy { customLimit() ?: DEFAULT_LIMIT }
    val fragmentLimit: Int by lazy { fragmentLimit() ?: DEFAULT_LIMIT }
    val tapLimit: Int by lazy { tapLimit() ?: DEFAULT_LIMIT }
    val webViewLimit: Int by lazy { webViewLimit() ?: DEFAULT_LIMIT }
    val captureViewClickCoordinates: Boolean by lazy { captureViewClickCoordinates() ?: false }
    val captureActivities: Boolean by lazy { captureActivities() ?: true }
    val captureWebViews: Boolean by lazy { captureWebViews() ?: true }
    val captureWebViewQueryParams: Boolean by lazy { captureWebViewQueryParams() ?: true }
    val webViewFragmentCapture: WebViewFragmentCapture by lazy { webViewFragmentCapture() ?: WebViewFragmentCapture.KEEP }
    val captureFcmPiiData: Boolean by lazy { captureFcmPiiData() ?: false }

    companion object {
        const val DEFAULT_LIMIT: Int = 100
    }
}
