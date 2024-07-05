package io.embrace.android.embracesdk.internal.api

public interface WebViewApi {
    public fun logWebView(url: String?)
    public fun trackWebViewPerformance(tag: String, message: String)
}
