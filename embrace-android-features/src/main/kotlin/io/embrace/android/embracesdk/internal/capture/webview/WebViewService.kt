package io.embrace.android.embracesdk.internal.capture.webview

/**
 * Collects WebViews information, like view properties, console logs, or core web vitals.
 */
interface WebViewService {

    /**
     * Collects WebView logs triggered by the Embrace JS Plugin.
     *
     * @param tag       a name for the WebView
     * @param message   the console message to process
     *
     */
    fun collectWebData(tag: String, message: String)
}
