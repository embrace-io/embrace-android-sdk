package io.embrace.android.embracesdk.internal.network.http;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import io.embrace.android.embracesdk.Embrace;

/**
 * HTTP-specific implementation of EmbraceURLStreamHandler.
 */
final class EmbraceHttpUrlStreamHandler extends EmbraceUrlStreamHandler {
    static final int PORT = 80;

    /**
     * Given the base URLStreamHandler that will be wrapped, constructs the instance.
     */
    public EmbraceHttpUrlStreamHandler(URLStreamHandler handler) {
        super(handler);
    }

    EmbraceHttpUrlStreamHandler(URLStreamHandler handler, Embrace embrace) {
        super(handler, embrace);
    }

    @Override
    public int getDefaultPort() {
        return PORT;
    }

    @Override
    protected Method getMethodOpenConnection(Class<URL> url) throws NoSuchMethodException {
        Method method = this.handler.getClass().getDeclaredMethod(METHOD_NAME_OPEN_CONNECTION, url);

        method.setAccessible(true);
        return method;
    }

    @Override
    protected Method getMethodOpenConnection(Class<URL> url, Class<Proxy> proxy) throws NoSuchMethodException {
        Method method = this.handler.getClass().getDeclaredMethod(METHOD_NAME_OPEN_CONNECTION, url, proxy);

        method.setAccessible(true);
        return method;
    }

    @Override
    protected URLConnection newEmbraceUrlConnection(URLConnection connection) {
        if (!(connection instanceof HttpURLConnection) || !Embrace.getInstance().isStarted()) {
            return connection;
        }

        injectTraceparent(connection);

        if (enableRequestSizeCapture && !connection.getRequestProperties().containsKey("Accept-Encoding")) {
            // This disables automatic gzip decompression by HttpUrlConnection so that we can
            // accurately count the number of bytes. We handle the decompression ourselves.
            connection.setRequestProperty("Accept-Encoding", "gzip");
            return new EmbraceHttpUrlConnectionImpl<>((HttpURLConnection) connection, true);
        } else {
            // Do not transparently decompress if the user has specified an encoding themselves.
            // Even if they pass in 'gzip', we should return them the compressed response.
            return new EmbraceHttpUrlConnectionImpl<>((HttpURLConnection) connection, false);
        }
    }
}
