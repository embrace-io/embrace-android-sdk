package io.embrace.android.embracesdk;

import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;

import io.embrace.android.embracesdk.annotation.InternalApi;

/**
 * @hide
 */
@InternalApi
public final class WebViewChromeClientSwazzledHooks {

    private WebViewChromeClientSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    public static void _preOnConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
    }
}
