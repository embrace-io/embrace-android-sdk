package io.embrace.android.embracesdk;

import static io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.logger;

import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;

import io.embrace.android.embracesdk.annotation.InternalApi;

@InternalApi
public final class WebViewChromeClientSwazzledHooks {

    private WebViewChromeClientSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    public static void _preOnConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
        logger.logInfo("webview _preOnConsoleMessage");
    }
}
