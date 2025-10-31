package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Captures custom breadcrumbs.
 */
class WebViewUrlDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    destination: TelemetryDestination,
    logger: EmbLogger,
    private val clock: Clock,
) : DataSourceImpl(
    destination = destination,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getWebViewBreadcrumbLimit)
) {

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
