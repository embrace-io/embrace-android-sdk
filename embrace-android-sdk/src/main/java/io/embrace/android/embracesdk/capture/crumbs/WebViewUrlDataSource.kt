package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Captures custom breadcrumbs.
 */
internal class WebViewUrlDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    writer: SessionSpanWriter,
    private val logger: EmbLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getWebViewBreadcrumbLimit)
) {

    companion object {
        private const val QUERY_PARAMETER_DELIMITER = "?"
    }

    fun logWebView(url: String?, startTime: Long) {
        try {
            captureData(
                inputValidation = {
                    url != null
                },
                captureAction = {
                    // Check if web view query params should be captured.
                    var parsedUrl: String = url ?: ""
                    if (!breadcrumbBehavior.isQueryParamCaptureEnabled()) {
                        val queryOffset = url?.indexOf(QUERY_PARAMETER_DELIMITER) ?: 0
                        if (queryOffset > 0) {
                            parsedUrl = url?.substring(0, queryOffset) ?: ""
                        }
                    }
                    addEvent(SchemaType.WebViewUrl(parsedUrl), startTime)
                }
            )
        } catch (ex: Exception) {
            logger.logError("Failed to log WebView breadcrumb for url $url")
        }
    }
}
