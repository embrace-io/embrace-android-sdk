package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import io.embrace.android.embracesdk.internal.instrumentation.webview.webViewUrlDataSource

@Keep
object WebViewClientBytecodeEntrypoint {

    @JvmStatic
    @Keep
    fun onPageStarted(url: String?) {
        webViewUrlDataSource?.logWebView(url)
    }
}
