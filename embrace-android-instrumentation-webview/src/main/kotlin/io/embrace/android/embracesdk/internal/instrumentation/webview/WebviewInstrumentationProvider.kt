package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

// retain a reference for use in bytecode instrumentation
internal var webViewUrlDataSource: WebViewUrlDataSource? = null

class WebviewInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                webViewUrlDataSource = WebViewUrlDataSource(args)
                webViewUrlDataSource
            },
            configGate = { args.configService.breadcrumbBehavior.isWebViewBreadcrumbCaptureEnabled() }
        )
    }
}
