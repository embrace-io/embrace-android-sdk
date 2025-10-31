package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

// retain a reference for use in bytecode instrumentation
var webViewUrlDataSource: WebViewUrlDataSource? = null

class WebviewInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                webViewUrlDataSource = WebViewUrlDataSource(args)
                webViewUrlDataSource
            },
            configGate = { args.configService.breadcrumbBehavior.isWebViewBreadcrumbCaptureEnabled() }
        )
    }
}
