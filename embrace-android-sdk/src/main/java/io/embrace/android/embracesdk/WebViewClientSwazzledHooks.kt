package io.embrace.android.embracesdk

import android.graphics.Bitmap
import android.webkit.WebView
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @hide
 */
@InternalApi
public object WebViewClientSwazzledHooks {

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    public fun _preOnPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        Embrace.getInstance().logWebView(url)
    }
}
