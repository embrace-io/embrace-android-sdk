package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture

/**
 * Captures the URLs of pages loaded in a webview.
 */
class WebViewUrlDataSource(
    args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy(args.configService.breadcrumbBehavior::getWebViewBreadcrumbLimit),
    instrumentationName = "webview_url_data_source",
) {

    private val breadcrumbBehavior: BreadcrumbBehavior = args.configService.breadcrumbBehavior

    fun logWebView(url: String?) {
        captureTelemetry(inputValidation = { url != null }) {
            addSessionPartEvent(SchemaType.WebViewUrl(sanitizeUrl(url ?: "")), clock.now())
        }
    }

    /**
     * Applies the query and fragment settings to a captured URL. The URL is split once at the first
     * '#' so that each setting applies to its own component and neither depends on the other.
     */
    private fun sanitizeUrl(url: String): String {
        val fragmentOffset = url.indexOf('#')
        val hasFragment = fragmentOffset >= 0
        val base = if (hasFragment) url.substring(0, fragmentOffset) else url
        val fragment = if (hasFragment) url.substring(fragmentOffset + 1) else ""

        val capturedBase = when {
            breadcrumbBehavior.isWebViewBreadcrumbQueryParamCaptureEnabled() -> base
            else -> base.substringBefore('?')
        }

        if (!hasFragment) {
            return capturedBase
        }
        return when (breadcrumbBehavior.getWebViewBreadcrumbFragmentCapture()) {
            WebViewFragmentCapture.KEEP -> "$capturedBase#$fragment"
            WebViewFragmentCapture.REDACT -> capturedBase + "#" + UrlFragmentRedactor.redact(fragment)
            WebViewFragmentCapture.REMOVE -> capturedBase
        }
    }
}
