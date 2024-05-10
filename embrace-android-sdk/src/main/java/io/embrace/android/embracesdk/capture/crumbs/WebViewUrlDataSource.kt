package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.SpanEventMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.WebViewBreadcrumb

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
    limitStrategy = UpToLimitStrategy(logger, breadcrumbBehavior::getWebViewBreadcrumbLimit)
),
    SpanEventMapper<WebViewBreadcrumb> {

    companion object {
        private const val QUERY_PARAMETER_DELIMITER = "?"
    }

    fun logWebView(url: String?, startTime: Long) {
        try {
            alterSessionSpan(
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

                    val crumb = WebViewBreadcrumb(parsedUrl, startTime)
                    addEvent(crumb, ::toSpanEventData)
                }
            )
        } catch (ex: Exception) {
            logger.logError("Failed to log WebView breadcrumb for url $url")
        }
    }

    override fun toSpanEventData(obj: WebViewBreadcrumb): SpanEventData {
        return SpanEventData(
            SchemaType.WebViewUrl(
                obj.url
            ),
            obj.startTime.millisToNanos()
        )
    }
}
