package io.embrace.android.embracesdk.internal.network.http;

import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import javax.net.ssl.HttpsURLConnection;

import io.embrace.android.embracesdk.Embrace;

/**
 * HTTPS-specific implementation of EmbraceUrlStreamHandler.
 */
final class EmbraceHttpsUrlStreamHandler extends EmbraceUrlStreamHandler {
    static final int PORT = 443;

    /**
     * Given the base URLStreamHandler that will be wrapped, constructs the instance.
     */
    public EmbraceHttpsUrlStreamHandler(URLStreamHandler handler) {
        super(handler);
    }

    EmbraceHttpsUrlStreamHandler(URLStreamHandler handler, Embrace embrace) {
        super(handler, embrace);
    }

    @Override
    public int getDefaultPort() {
        return PORT;
    }

    @Override
    protected Method getMethodOpenConnection(Class<URL> url) throws NoSuchMethodException {
        Method method = this.handler.getClass().getSuperclass().getDeclaredMethod(METHOD_NAME_OPEN_CONNECTION, url);

        method.setAccessible(true);
        return method;
    }

    @Override
    protected Method getMethodOpenConnection(Class<URL> url, Class<Proxy> proxy) throws NoSuchMethodException {
        Method method = this.handler.getClass().getSuperclass().getDeclaredMethod(METHOD_NAME_OPEN_CONNECTION, url, proxy);

        method.setAccessible(true);
        return method;
    }

    @Override
    protected URLConnection newEmbraceUrlConnection(URLConnection connection) {
        if (!(connection instanceof HttpsURLConnection)) {
            return connection;
        }

        injectTraceparent(connection);

        if (enableRequestSizeCapture && !connection.getRequestProperties().containsKey("Accept-Encoding")) {
            // This disables automatic gzip decompression by HttpUrlConnection so that we can
            // accurately count the number of bytes. We handle the decompression ourselves.
            connection.setRequestProperty("Accept-Encoding", "gzip");
            return new EmbraceHttpsUrlConnectionImpl<>((HttpsURLConnection) connection, true);
        } else {
            // Do not transparently decompress if the user has specified an encoding themselves.
            // Even if they pass in 'gzip', we should return them the compressed response.
            return new EmbraceHttpsUrlConnectionImpl<>((HttpsURLConnection) connection, false);
        }
    }
}
