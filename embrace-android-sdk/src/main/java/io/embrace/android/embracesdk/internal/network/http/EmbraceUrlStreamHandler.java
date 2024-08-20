package io.embrace.android.embracesdk.internal.network.http;

import static io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl.TRACEPARENT_HEADER_NAME;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Custom implementation of URLStreamHandler that wraps a base URLStreamHandler and provides a context for executing
 * Embrace-specific logic.
 */
abstract class EmbraceUrlStreamHandler extends URLStreamHandler {
    protected static final String METHOD_NAME_OPEN_CONNECTION = "openConnection";

    protected static final String MSG_ERROR_OPEN_CONNECTION =
        "An exception was thrown while attempting to open a connection";

    protected final InternalNetworkApi internalNetworkApi;

    protected final URLStreamHandler handler;

    /**
     * Method that corresponds to URLStreamHandler.openConnection(URL).
     */
    private final Method methodOpenConnection1;

    /**
     * Method that corresponds to URLStreamHandler.openConnection(URL, Proxy).
     */
    private final Method methodOpenConnection2;

    /**
     * This enables or disables automatic gzip decompression
     */
    protected static Boolean enableRequestSizeCapture = false;

    /**
     * Given the base URLStreamHandler that will be wrapped, constructs the instance.
     */
    public EmbraceUrlStreamHandler(@NonNull URLStreamHandler handler) {
        this(handler, InternalNetworkApiImplKt.getInstance());
    }

    EmbraceUrlStreamHandler(@NonNull URLStreamHandler handler, @NonNull InternalNetworkApi internalNetworkApi) {
        this.handler = handler;
        this.internalNetworkApi = internalNetworkApi;
        try {
            this.methodOpenConnection1 = getMethodOpenConnection(URL.class);
            this.methodOpenConnection2 = getMethodOpenConnection(URL.class, Proxy.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Failed to initialize EmbraceUrlStreamHandler instance.", e);
        }
    }

    @Override
    public abstract int getDefaultPort();

    /**
     * Sets the Request Size Capture flag value
     */
    public static void setEnableRequestSizeCapture(Boolean value) {
        enableRequestSizeCapture = value;
    }

    /**
     * Given the URL class instance, returns the Java method that corresponds to the URLStreamHandler.openConnection(URL)
     * method.
     */
    protected abstract Method getMethodOpenConnection(Class<URL> url) throws NoSuchMethodException;

    /**
     * Given the URL class and Proxy class instances, returns the Java method that corresponds to the
     * URLStreamHandler.openConnection(URL, Proxy) method.
     */
    protected abstract Method getMethodOpenConnection(Class<URL> url, Class<Proxy> proxy) throws NoSuchMethodException;

    /**
     * Given an instance of URLConnection, returns a new URLConnection that wraps the provided instance with additional
     * Embrace-specific logic.
     */
    protected abstract URLConnection wrapUrlConnection(URLConnection connection);

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        try {
            return newUrlConnection((URLConnection) this.methodOpenConnection1.invoke(this.handler, url));
        } catch (Exception e) {
            // We catch Exception here instead of the specific exceptions that can be thrown due to a change in the way some
            // of these exceptions are compiled on different OS versions.

            throw new IOException(MSG_ERROR_OPEN_CONNECTION, e);
        }
    }

    @Override
    protected URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        try {
            return newUrlConnection((URLConnection) this.methodOpenConnection2.invoke(this.handler, url, proxy));
        } catch (Exception e) {
            // We catch Exception here instead of the specific exceptions that can be thrown due to a change in the way some
            // of these exceptions are compiled on different OS versions.

            throw new IOException(MSG_ERROR_OPEN_CONNECTION, e);
        }
    }

    protected void injectTraceparent(@NonNull URLConnection connection) {
        boolean networkSpanForwardingEnabled = internalNetworkApi.isNetworkSpanForwardingEnabled();
        if (networkSpanForwardingEnabled && !connection.getRequestProperties().containsKey(TRACEPARENT_HEADER_NAME)) {
            connection.addRequestProperty(TRACEPARENT_HEADER_NAME, internalNetworkApi.generateW3cTraceparent());
        }
    }

    /**
     * If sdk is not started we won't wrap url connection. Instead, we return the original connection.
     * @param connection
     * @return
     */
    private URLConnection newUrlConnection(URLConnection connection) {
        if (internalNetworkApi.isStarted()) {
            return wrapUrlConnection(connection);
        } else {
            return connection;
        }
    }
}
