package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior

/**
 * Captures custom breadcrumbs.
 */
class WebViewUrlDataSource(
    args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy(args.configService.breadcrumbBehavior::getWebViewBreadcrumbLimit),
    instrumentationName = "webview_url_data_source"
) {

    private val breadcrumbBehavior: BreadcrumbBehavior = args.configService.breadcrumbBehavior

    private companion object {
        private const val QUERY_PARAMETER_DELIMITER = "?"
    }

    fun logWebView(url: String?) {
        captureTelemetry(inputValidation = { url != null }) {
            // Check if web view query params should be captured.
            var parsedUrl: String = url ?: ""
            if (!breadcrumbBehavior.isWebViewBreadcrumbQueryParamCaptureEnabled()) {
                val queryOffset = url?.indexOf(QUERY_PARAMETER_DELIMITER) ?: 0
                if (queryOffset > 0) {
                    parsedUrl = url?.substring(0, queryOffset) ?: ""
                }
            }
            addSessionEvent(SchemaType.WebViewUrl(parsedUrl), clock.now())
        }
    }
}
