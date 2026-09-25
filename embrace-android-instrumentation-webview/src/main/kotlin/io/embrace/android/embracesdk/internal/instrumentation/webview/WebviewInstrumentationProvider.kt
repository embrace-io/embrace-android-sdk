package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

// retain a reference for use in bytecode instrumentation
internal var webViewUrlDataSource: WebViewUrlDataSource? = null

class WebviewInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceState<DataSource>? {
        return {
            if (args.configService.breadcrumbBehavior.isWebViewBreadcrumbCaptureEnabled()) {
                webViewUrlDataSource = WebViewUrlDataSource(args)
                webViewUrlDataSource
            } else {
                null
            }
        }
    }
}
