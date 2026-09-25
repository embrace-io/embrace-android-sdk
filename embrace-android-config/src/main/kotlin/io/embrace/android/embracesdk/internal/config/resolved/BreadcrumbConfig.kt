package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture

/**
 * Resolved breadcrumb config.
 */
interface BreadcrumbConfig {
    val customLimit: Int
    val fragmentLimit: Int
    val tapLimit: Int
    val webViewLimit: Int
    val captureViewClickCoordinates: Boolean
    val captureActivities: Boolean
    val captureWebViews: Boolean
    val captureWebViewQueryParams: Boolean
    val webViewFragmentCapture: WebViewFragmentCapture
    val captureFcmPiiData: Boolean

    companion object {
        const val DEFAULT_LIMIT: Int = 100
    }
}

/**
 * Creates a [BreadcrumbConfig]. A provider returning null means the default is used.
 */
inline fun BreadcrumbConfig(
    crossinline customLimit: () -> Int? = { null },
    crossinline fragmentLimit: () -> Int? = { null },
    crossinline tapLimit: () -> Int? = { null },
    crossinline webViewLimit: () -> Int? = { null },
    crossinline captureViewClickCoordinates: () -> Boolean? = { null },
    crossinline captureActivities: () -> Boolean? = { null },
    crossinline captureWebViews: () -> Boolean? = { null },
    crossinline captureWebViewQueryParams: () -> Boolean? = { null },
    crossinline webViewFragmentCapture: () -> WebViewFragmentCapture? = { null },
    crossinline captureFcmPiiData: () -> Boolean? = { null },
): BreadcrumbConfig = object : BreadcrumbConfig {
    override val customLimit: Int = customLimit() ?: BreadcrumbConfig.DEFAULT_LIMIT
    override val fragmentLimit: Int = fragmentLimit() ?: BreadcrumbConfig.DEFAULT_LIMIT
    override val tapLimit: Int = tapLimit() ?: BreadcrumbConfig.DEFAULT_LIMIT
    override val webViewLimit: Int = webViewLimit() ?: BreadcrumbConfig.DEFAULT_LIMIT
    override val captureViewClickCoordinates: Boolean = captureViewClickCoordinates() ?: false
    override val captureActivities: Boolean = captureActivities() ?: true
    override val captureWebViews: Boolean = captureWebViews() ?: true
    override val captureWebViewQueryParams: Boolean = captureWebViewQueryParams() ?: true
    override val webViewFragmentCapture: WebViewFragmentCapture = webViewFragmentCapture() ?: WebViewFragmentCapture.KEEP
    override val captureFcmPiiData: Boolean = captureFcmPiiData() ?: false
}
