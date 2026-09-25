package io.embrace.android.embracesdk.internal.instrumentation.webview

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

// retain a reference for use in bytecode instrumentation
internal var webViewUrlDataSource: WebViewUrlDataSource? = null

class WebviewInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.config.breadcrumb.captureWebViews) {
                webViewUrlDataSource = WebViewUrlDataSource(args)
                webViewUrlDataSource
            } else {
                null
            }
        }
    }
}
