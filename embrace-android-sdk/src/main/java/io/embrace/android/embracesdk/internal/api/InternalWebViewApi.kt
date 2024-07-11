package io.embrace.android.embracesdk.internal.api

import android.webkit.ConsoleMessage

public interface InternalWebViewApi {
    public fun logWebView(url: String?)
    public fun trackWebViewPerformance(tag: String, consoleMessage: ConsoleMessage)
    public fun trackWebViewPerformance(tag: String, message: String)
}
