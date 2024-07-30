package io.embrace.android.embracesdk.internal.api

internal interface WebViewApi {
    fun logWebView(url: String?)
    fun trackWebViewPerformance(tag: String, message: String)
}
