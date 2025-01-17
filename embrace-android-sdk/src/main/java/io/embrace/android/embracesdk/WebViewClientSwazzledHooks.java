package io.embrace.android.embracesdk;

import androidx.annotation.Nullable;

import io.embrace.android.embracesdk.annotation.InternalApi;

/**
 * @hide
 */
@InternalApi
public final class WebViewClientSwazzledHooks {

    private WebViewClientSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    public static void _preOnPageStarted(@Nullable android.webkit.WebView view,
                                         @Nullable java.lang.String url,
                                         @Nullable android.graphics.Bitmap favicon) {
        Embrace.getInstance().logWebView(url);
    }
}
