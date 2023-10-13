package io.embrace.android.embracesdk.capture.webview

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.WebViewInfo

/**
 * Collects WebViews information, like view properties, console logs, or core web vitals.
 */
internal interface WebViewService : DataCaptureService<List<WebViewInfo>?> {

    /**
     * Collects WebView logs triggered by the Embrace JS Plugin.
     *
     * @param tag       a name for the WebView
     * @param message   the console message to process
     *
     */
    fun collectWebData(tag: String, message: String)
}
