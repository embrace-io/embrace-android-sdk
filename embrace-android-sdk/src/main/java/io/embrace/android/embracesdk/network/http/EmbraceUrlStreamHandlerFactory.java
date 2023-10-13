package io.embrace.android.embracesdk.network.http;

import android.os.Build;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.Map;

import io.embrace.android.embracesdk.InternalApi;
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;
import io.embrace.android.embracesdk.network.http.EmbraceHttpUrlStreamHandler;
import io.embrace.android.embracesdk.network.http.EmbraceHttpsUrlStreamHandler;

/**
 * Custom implementation of URLStreamHandlerFactory that is able to return URLStreamHandlers that log network data to
 * Embrace.
 */
final class EmbraceUrlStreamHandlerFactory implements URLStreamHandlerFactory {

    private static final String PROTOCOL_HTTP = "http";
    private static final String PROTOCOL_HTTPS = "https";

    private static final String CLASS_HTTP_LIBCORE_STREAM_HANDLER = "libcore.net.http.HttpHandler";
    private static final String CLASS_HTTP_OKHTTP_STREAM_HANDLER = "com.android.okhttp.HttpHandler";

    private static final String CLASS_HTTPS_LIBCORE_STREAM_HANDLER = "libcore.net.http.HttpsHandler";
    private static final String CLASS_HTTPS_OKHTTP_STREAM_HANDLER = "com.android.okhttp.HttpsHandler";

    private static final Map<String, URLStreamHandler> handlers = new HashMap<>();

    static {
        try {
            // Pre-allocate and cache these stream handlers up front so no pre-fetch checks are required later.
            handlers.put(PROTOCOL_HTTP, new EmbraceHttpUrlStreamHandler(newUrlStreamHandler(CLASS_HTTP_OKHTTP_STREAM_HANDLER)));
            handlers.put(PROTOCOL_HTTPS, new EmbraceHttpsUrlStreamHandler(newUrlStreamHandler(CLASS_HTTPS_OKHTTP_STREAM_HANDLER)));
        } catch (Exception ex) {
            InternalStaticEmbraceLogger.logError("Failed initialize EmbraceUrlStreamHandlerFactory", ex);
        }
    }

    static URLStreamHandler newUrlStreamHandler(String className) {
        try {
            return (URLStreamHandler) Class.forName(className).newInstance();
        } catch (Exception e) {
            // We catch Exception here instead of the specific exceptions that can be thrown due to a change in the way some
            // of these exceptions are compiled on different OS versions.

            // TODO: Uncomment this after supporting dependency injection for EmbLogger.
            // EmbLogger.logError("Failed to instantiate new URLStreamHandler instance: " + className, e);
            return null;
        }
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return protocol != null ? handlers.get(protocol) : null;
    }
}
