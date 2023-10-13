package io.embrace.android.embracesdk;

@InternalApi
public final class WebViewClientSwazzledHooks {

    private WebViewClientSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    public static void _preOnPageStarted(android.webkit.WebView view,
                                         java.lang.String url,
                                         android.graphics.Bitmap favicon) {

        Embrace.getImpl().logWebView(url);
    }
}
