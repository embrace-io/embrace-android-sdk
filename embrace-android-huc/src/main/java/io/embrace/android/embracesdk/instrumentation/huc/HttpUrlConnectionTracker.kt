package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import javax.net.ssl.HttpsURLConnection

/**
 * Singleton that interacts with static fields of the HttpUrlConnection API to hookup instrumentation
 */
@InternalApi
internal object HttpUrlConnectionTracker {
    private var internalNetworkApi: InternalNetworkApi = NoopInternalNetworkApi
    private val factoryField: Field
        /**
         * Gets the field within [URL] holding the factory.
         *
         * @return the field holding the factory.
         */
        get() {
            // Use reflection to get the field holding the factory
            val fields = URL::class.java.declaredFields
            for (current in fields) {
                if (Modifier.isStatic(current.modifiers) && current.type == URLStreamHandlerFactory::class.java) {
                    current.isAccessible = true
                    return current
                }
            }
            error("Unable to detect static field in the URL class for the URLStreamHandlerFactory.")
        }

    /**
     * Installs the correct type of [URLStreamHandlerFactory] in order to intercept network
     * traffic in a way which is compatible with other SDKs. `URL.setURLStreamHandlerFactory` is
     * a singleton, so if an existing factory is already registered, it is wrapped using reflection so
     * that Embrace can intercept network traffic.
     *
     *
     * This relies on the Embrace SDK being initialized second, so that the Embrace SDK is able to
     * detect an existing [URLStreamHandlerFactory] and wrap it with its interception logic.
     */
    fun registerUrlStreamHandlerFactory(requestContentLengthCaptureEnabled: Boolean, sdkApi: SdkApi) {
        internalNetworkApi = InternalNetworkApiImpl(sdkApi)
        registerFactory(requestContentLengthCaptureEnabled)
    }

    fun getInternalNetworkApi(): InternalNetworkApi = internalNetworkApi

    /**
     * Registers either a [EmbraceUrlStreamHandlerFactory] or a [WrappingFactory]
     * depending on whether or not an existing factory has already been registered.
     *
     * If there is an exception thrown when attempting to detect or wrap the third-party factory
     * using reflection, the method will fall back to trying to register a
     * [EmbraceUrlStreamHandlerFactory] in the typical way.
     */
    private fun registerFactory(enableRequestSizeCapture: Boolean) {
        EmbraceUrlStreamHandler.setEnableRequestSizeCapture(enableRequestSizeCapture)

        try {
            val existingFactory: Any? = factoryField.get(null)
            if (existingFactory == null) {
                // No factory is registered, so we can simply register the Embrace factory
                URL.setURLStreamHandlerFactory(EmbraceUrlStreamHandlerFactory())
            } else {
                val wrappingFactory =
                    WrappingFactory(existingFactory as URLStreamHandlerFactory, enableRequestSizeCapture)
                clearFactory()
                URL.setURLStreamHandlerFactory(wrappingFactory)
            }
        } catch (ex: Throwable) {
            // Catching Throwable as URL.setURLStreamHandlerFactory throws an Error which we want to
            // handle, rather than kill the application if we are unable to swap the factory.
            internalNetworkApi.logInternalError(ex)
            try {
                URL.setURLStreamHandlerFactory(EmbraceUrlStreamHandlerFactory())
            } catch (ex2: Throwable) {
                internalNetworkApi.logInternalError(ex2)
            }
        }
    }

    /**
     * Forcibly clears the existing factory from [URL] to allow us to attach a new one.
     *
     * The new factory must not be set on the field directly, as [URL] caches each
     * [URLStreamHandler]. By clearing the factory and then calling
     * `URL.setURLStreamHandlerFactory`, we ensure that the cached handlers are cleared.
     */
    private fun clearFactory() {
        try {
            val factoryField: Field = factoryField
            factoryField.set(null, null)
        } catch (ex: Exception) {
            throw propagate(ex)
        }
    }

    /**
     * A factory which generates a [URLConnection], wrapping the [URLConnection] provided
     * by the wrapped factory. The Embrace network logging is performed, then we delegate to the
     * third-party [URLConnection].
     */
    private class WrappingFactory(
        private val parent: URLStreamHandlerFactory,
        private val enableRequestSizeCapture: Boolean,
    ) : URLStreamHandlerFactory {
        override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
            val parentHandler: URLStreamHandler?
            try {
                parentHandler = parent.createURLStreamHandler(protocol)
            } catch (ex: Exception) {
                internalNetworkApi.logInternalError(ex)
                return EmbraceUrlStreamHandlerFactory().createURLStreamHandler(protocol)
            }
            if (parentHandler == null) {
                // Fall back to the Embrace factory if the parent handler doesn't support the protocol
                return EmbraceUrlStreamHandlerFactory().createURLStreamHandler(protocol)
            }

            return object : URLStreamHandler() {
                override fun openConnection(url: URL?, proxy: Proxy?): URLConnection? {
                    try {
                        val method =
                            findDeclaredMethod(
                                parentHandler,
                                parentHandler.javaClass,
                                EmbraceUrlStreamHandler.METHOD_NAME_OPEN_CONNECTION,
                                URL::class.java,
                                Proxy::class.java
                            )
                        method.isAccessible = true
                        val parentConnection = method.invoke(parentHandler, url, proxy) as URLConnection?
                        return wrapConnection(parentConnection)
                    } catch (ex: Exception) {
                        internalNetworkApi.logInternalError(ex)
                        throw propagate(ex)
                    }
                }

                override fun openConnection(url: URL?): URLConnection? {
                    try {
                        val method =
                            findDeclaredMethod(
                                parentHandler,
                                parentHandler.javaClass,
                                EmbraceUrlStreamHandler.METHOD_NAME_OPEN_CONNECTION,
                                URL::class.java,
                                Proxy::class.java
                            )
                        method.isAccessible = true
                        val parentConnection = method.invoke(parentHandler, url) as URLConnection?
                        return wrapConnection(parentConnection)
                    } catch (ex: Exception) {
                        internalNetworkApi.logInternalError(ex)
                        throw propagate(ex)
                    }
                }

                fun wrapConnection(parentConnection: URLConnection?): URLConnection? {
                    return if (parentConnection is HttpURLConnection) {
                        var transparentGzip = false
                        if (enableRequestSizeCapture && !parentConnection.getRequestProperties().containsKey("Accept-Encoding")) {
                            // This disables automatic gzip decompression by HttpUrlConnection so that we can
                            // accurately count the number of bytes. We handle the decompression ourselves.
                            parentConnection.setRequestProperty("Accept-Encoding", "gzip")
                            transparentGzip = true
                        }
                        if (parentConnection is HttpsURLConnection) {
                            EmbraceHttpsUrlConnectionImpl(parentConnection, transparentGzip)
                        } else {
                            EmbraceHttpUrlConnectionImpl(parentConnection, transparentGzip)
                        }
                    } else {
                        parentConnection
                    }
                }
            }
        }
    }

    /**
     * Custom implementation of URLStreamHandlerFactory that is able to return URLStreamHandlers that log network data to
     * Embrace.
     */
    class EmbraceUrlStreamHandlerFactory : URLStreamHandlerFactory {
        override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
            return if (protocol != null) handlers[protocol] else null
        }

        companion object {
            private const val PROTOCOL_HTTP = "http"
            private const val PROTOCOL_HTTPS = "https"
            private const val CLASS_HTTP_OKHTTP_STREAM_HANDLER = "com.android.okhttp.HttpHandler"
            private const val CLASS_HTTPS_OKHTTP_STREAM_HANDLER = "com.android.okhttp.HttpsHandler"
            private val handlers: MutableMap<String?, URLStreamHandler?> = HashMap<String?, URLStreamHandler?>()

            init {
                try {
                    // Pre-allocate and cache these stream handlers up front so no pre-fetch checks are required later.
                    handlers.put(PROTOCOL_HTTP, EmbraceHttpUrlStreamHandler(newUrlStreamHandler(CLASS_HTTP_OKHTTP_STREAM_HANDLER)))
                    handlers.put(PROTOCOL_HTTPS, EmbraceHttpsUrlStreamHandler(newUrlStreamHandler(CLASS_HTTPS_OKHTTP_STREAM_HANDLER)))
                } catch (ex: Exception) {
                    internalNetworkApi.logInternalError(ex)
                }
            }

            fun newUrlStreamHandler(className: String): URLStreamHandler? {
                try {
                    return Class.forName(className).getDeclaredConstructor().newInstance() as URLStreamHandler
                } catch (e: Exception) {
                    // We catch Exception here instead of the specific exceptions that can be thrown due to a change in the way some
                    // of these exceptions are compiled on different OS versions.
                    internalNetworkApi.logInternalError(e)
                    return null
                }
            }
        }
    }
}

/**
 * Propagates `throwable` as-is if possible, or by wrapping in a `RuntimeException` if not.
 *
 *  * If `throwable` is an `InvocationTargetException` the cause is extracted and processed recursively.
 *  * If `throwable` is an `InterruptedException` then the thread is interrupted and a `RuntimeException` is thrown.
 *  * If `throwable` is an `Error` or `RuntimeException` it is propagated as-is.
 *  * Otherwise `throwable` is wrapped in a `RuntimeException` and thrown.
 *
 * This method always throws an exception. The return type is a convenience to satisfy the type system
 * when the enclosing method returns a value. For example:
 * ```
 * T foo() {
 * try {
 * return methodWithCheckedException();
 * } catch (Exception e) {
 * throw Unchecked.propagate(e);
 * }
 * }
 * ```
 *
 * @param throwable the `Throwable` to propagate
 * @return nothing; this method always throws an exception
 */
internal fun propagate(throwable: Throwable?): RuntimeException {
    if (throwable is InvocationTargetException) {
        throw propagate(throwable.cause)
    } else {
        if (throwable is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        @Suppress("TooGenericExceptionThrown") // maintain backwards compat.
        throw RuntimeException(throwable)
    }
}
