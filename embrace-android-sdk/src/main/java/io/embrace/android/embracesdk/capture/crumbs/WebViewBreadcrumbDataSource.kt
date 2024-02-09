package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.WebViewBreadcrumb

/**
 * Captures webview breadcrumbs.
 */
internal class WebViewBreadcrumbDataSource(
    private val configService: ConfigService,
    private val store: BreadcrumbDataStore<WebViewBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getWebViewBreadcrumbLimit()
    },
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataCaptureService<List<WebViewBreadcrumb>> by store {

    companion object {
        private const val QUERY_PARAMETER_DELIMITER = "?"
    }

    fun logWebView(url: String?, startTime: Long) {
        if (!configService.breadcrumbBehavior.isWebViewBreadcrumbCaptureEnabled()) {
            return
        }
        if (url == null) {
            return
        }
        try {
            // Check if web view query params should be captured.
            var parsedUrl: String = url
            if (!configService.breadcrumbBehavior.isQueryParamCaptureEnabled()) {
                val queryOffset = url.indexOf(QUERY_PARAMETER_DELIMITER)
                if (queryOffset > 0) {
                    parsedUrl = url.substring(0, queryOffset)
                }
            }
            store.tryAddBreadcrumb(WebViewBreadcrumb(parsedUrl, startTime))
        } catch (ex: Exception) {
            logger.logError("Failed to log WebView breadcrumb for url $url")
        }
    }
}
