package io.embrace.android.embracesdk;

import io.embrace.android.embracesdk.annotation.InternalApi;

/**
 * @hide
 */
@InternalApi
final class WebViewClientSwazzledHooks {

    private WebViewClientSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    public static void _preOnPageStarted(android.webkit.WebView view,
                                         java.lang.String url,
                                         android.graphics.Bitmap favicon) {

        Embrace.getImpl().logWebView(url);
    }
}
