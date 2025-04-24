package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.annotation.Keep
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @hide
 */
@InternalApi
@Keep
public object WebViewClientBytecodeEntrypoint {

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    @Keep
    public fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?
    ) {
        Embrace.getInstance().logWebView(url)
    }
}
