package io.embrace.android.embracesdk.internal.capture.webview

/**
 * Collects WebViews information, like view properties, console logs, or core web vitals.
 */
internal interface WebViewService {

    /**
     * Collects WebView logs triggered by the Embrace JS Plugin.
     *
     * @param tag       a name for the WebView
     * @param message   the console message to process
     *
     */
    fun collectWebData(tag: String, message: String)

    /**
     * Loads the collected data into the session.
     * This method should be called when the session is being closed.
     * As there is a processing where the data can be overridden by a second message,
     * this method should be called once the session is ending.
     */
    fun loadDataIntoSession()
}
