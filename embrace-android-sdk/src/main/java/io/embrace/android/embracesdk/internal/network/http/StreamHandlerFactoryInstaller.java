package io.embrace.android.embracesdk.internal.network.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import javax.net.ssl.HttpsURLConnection;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.internal.utils.exceptions.Unchecked;

/**
 * Installs the correct type of {@link URLStreamHandlerFactory} in order to intercept network
 * traffic in a way which is compatible with other SDKs. {@code URL.setURLStreamHandlerFactory} is
 * a singleton, so if an existing factory is already registered, it is wrapped using reflection so
 * that Embrace can intercept network traffic.
 * <p>
 * This relies on the Embrace SDK being initialized second, so that the Embrace SDK is able to
 * detect an existing {@link URLStreamHandlerFactory} and wrap it with its interception logic.
 */
class StreamHandlerFactoryInstaller {

    private StreamHandlerFactoryInstaller() {
        // Restricted constructor
    }

    /**
     * Registers either a {@link EmbraceUrlStreamHandlerFactory} or a {@link WrappingFactory}
     * depending on whether or not an existing factory has already been registered.
     * <p>
     * If there is an exception thrown when attempting to detect or wrap the third-party factory
     * using reflection, the method will fall back to trying to register a
     * {@link EmbraceUrlStreamHandlerFactory} in the typical way.
     */
    static void registerFactory(Boolean enableRequestSizeCapture) {
        EmbraceUrlStreamHandler.setEnableRequestSizeCapture(enableRequestSizeCapture);

        try {
            Object existingFactory = getFactoryField().get(null);
            if (existingFactory == null) {
                // No factory is registered, so we can simply register the Embrace factory
                URL.setURLStreamHandlerFactory(new EmbraceUrlStreamHandlerFactory());
            } else {
                WrappingFactory wrappingFactory = new WrappingFactory((URLStreamHandlerFactory) existingFactory, enableRequestSizeCapture);
                clearFactory();
                URL.setURLStreamHandlerFactory(wrappingFactory);
            }
        } catch (Throwable ex) {
            // Catching Throwable as URL.setURLStreamHandlerFactory throws an Error which we want to
            // handle, rather than kill the application if we are unable to swap the factory.
            String msg = "Error during wrapping of UrlStreamHandlerFactory. Will attempt to set the default Embrace factory";
            logError(msg, ex);
            try {
                URL.setURLStreamHandlerFactory(new EmbraceUrlStreamHandlerFactory());
            } catch (Throwable ex2) {
                logError("Failed to register EmbraceUrlStreamHandlerFactory. Network capture disabled.", ex2);
            }
        }
    }

    /**
     * Gets the field within {@link URL} holding the factory.
     *
     * @return the field holding the factory.
     */
    private static Field getFactoryField() {
        // Use reflection to get the field holding the factory
        final Field[] fields = URL.class.getDeclaredFields();
        for (Field current : fields) {
            if (Modifier.isStatic(current.getModifiers()) && current.getType().equals(URLStreamHandlerFactory.class)) {
                current.setAccessible(true);
                return current;
            }
        }
        throw new IllegalStateException("Unable to detect static field in the URL class for the URLStreamHandlerFactory.");
    }

    /**
     * Forcibly clears the existing factory from {@link URL} to allow us to attach a new one.
     * <p>
     * The new factory must not be set on the field directly, as {@link URL} caches each
     * {@link URLStreamHandler}. By clearing the factory and then calling
     * {@code URL.setURLStreamHandlerFactory}, we ensure that the cached handlers are cleared.
     */
    private static void clearFactory() {
        try {
            Field factoryField = getFactoryField();
            factoryField.set(null, null);
        } catch (Exception ex) {
            throw Unchecked.propagate(ex);
        }
    }

    static void logError(@NonNull String message, @Nullable Throwable throwable) {
        if (throwable != null) {
            Embrace.getInstance().getInternalInterface().logInternalError(throwable);
        }
    }

    /**
     * A factory which generates a {@link URLConnection}, wrapping the {@link URLConnection} provided
     * by the wrapped factory. The Embrace network logging is performed, then we delegate to the
     * third-party {@link URLConnection}.
     */
    private static class WrappingFactory implements URLStreamHandlerFactory {

        private final URLStreamHandlerFactory parent;

        /**
         * This enables or disables automatic gzip decompression
         */
        final Boolean enableRequestSizeCapture;

        /**
         * Creates an instance of the wrapping factory.
         *
         * @param parent                   the {@link URLStreamHandlerFactory} to wrap
         * @param enableRequestSizeCapture config flag to enabled content size measurements
         */
        WrappingFactory(@NonNull URLStreamHandlerFactory parent, Boolean enableRequestSizeCapture) {
            this.parent = parent;
            this.enableRequestSizeCapture = enableRequestSizeCapture;
        }

        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            URLStreamHandler parentHandler;
            try {
                parentHandler = parent.createURLStreamHandler(protocol);
            } catch (Exception ex) {
                String msg = "Exception when trying to create stream handler with parent factory for protocol: " + protocol;
                logError(msg, ex);
                return new EmbraceUrlStreamHandlerFactory().createURLStreamHandler(protocol);
            }
            if (parentHandler == null) {
                // Fall back to the Embrace factory if the parent handler doesn't support the protocol
                return new EmbraceUrlStreamHandlerFactory().createURLStreamHandler(protocol);
            }

            return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url, Proxy proxy) {
                    try {
                        Method method = parentHandler.getClass().getDeclaredMethod("openConnection", URL.class, Proxy.class);
                        method.setAccessible(true);
                        URLConnection parentConnection = (URLConnection) method.invoke(parentHandler, url, proxy);
                        return wrapConnection(parentConnection);
                    } catch (Exception ex) {
                        String msg = "Exception when opening connection for protocol: " + protocol + " and URL: " + url;
                        logError(msg, ex);
                        throw Unchecked.propagate(ex);
                    }
                }

                @Override
                protected URLConnection openConnection(URL url) {
                    try {
                        Method method = parentHandler.getClass().getDeclaredMethod("openConnection", URL.class);
                        method.setAccessible(true);
                        URLConnection parentConnection = (URLConnection) method.invoke(parentHandler, url);
                        return wrapConnection(parentConnection);
                    } catch (Exception ex) {
                        String msg = "Exception when opening connection for protocol: " + protocol + " and URL: " + url;
                        logError(msg, ex);
                        throw Unchecked.propagate(ex);
                    }
                }

                private URLConnection wrapConnection(URLConnection parentConnection) {
                    if (parentConnection instanceof HttpURLConnection) {
                        boolean transparentGzip = false;
                        if (enableRequestSizeCapture && !parentConnection.getRequestProperties().containsKey("Accept-Encoding")) {
                            // This disables automatic gzip decompression by HttpUrlConnection so that we can
                            // accurately count the number of bytes. We handle the decompression ourselves.
                            parentConnection.setRequestProperty("Accept-Encoding", "gzip");
                            transparentGzip = true;
                        }
                        if (parentConnection instanceof HttpsURLConnection) {
                            return new EmbraceHttpsUrlConnectionImpl<>((HttpsURLConnection) parentConnection, transparentGzip);
                        } else {
                            return new EmbraceHttpUrlConnectionImpl<>((HttpURLConnection) parentConnection, transparentGzip);

                        }
                    } else {
                        // We do not support wrapping this connection type
                        logError("Cannot wrap unsupported protocol: " + protocol, null);
                        return parentConnection;
                    }
                }
            };
        }
    }
}
