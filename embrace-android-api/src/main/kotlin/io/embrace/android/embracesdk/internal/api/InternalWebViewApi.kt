package io.embrace.android.embracesdk.internal.api

import android.webkit.ConsoleMessage
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @suppress
 */
@InternalApi
public interface InternalWebViewApi {

    /**
     * @suppress
     */
    public fun logWebView(url: String?)

    /**
     * @suppress
     */
    public fun trackWebViewPerformance(tag: String, consoleMessage: ConsoleMessage)

    /**
     * @suppress
     */
    public fun trackWebViewPerformance(tag: String, message: String)
}
