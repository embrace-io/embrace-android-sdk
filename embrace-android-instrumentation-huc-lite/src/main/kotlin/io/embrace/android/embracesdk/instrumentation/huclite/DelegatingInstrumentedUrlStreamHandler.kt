package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import javax.net.ssl.HttpsURLConnection

/**
 * [URLStreamHandler] that delegates [URLConnection] creation to [delegateHandler] and wraps the returned connection with a wrapper
 * that is instrumented if the connection is a [HttpsURLConnection]
 */
internal class DelegatingInstrumentedUrlStreamHandler(
    private val delegateHandler: URLStreamHandler,
    private val clock: Clock,
    private val dataSourceProvider: () -> HucLiteDataSource?,
) : URLStreamHandler() {
    private val openProxyConnectionMethod by LazyMethodLookup(
        delegateHandler::class.java,
        "openConnection",
        arrayOf(URL::class.java, Proxy::class.java),
    )

    private val openConnectionMethod by LazyMethodLookup(
        delegateHandler::class.java,
        "openConnection",
        arrayOf(URL::class.java),
    )

    @Throws(IOException::class)
    override fun openConnection(url: URL?, proxy: Proxy?): URLConnection? =
        wrapInstrumentedConnection(
            invokeDelegate { openProxyConnectionMethod.invoke(delegateHandler, url, proxy) },
        )

    @Throws(IOException::class)
    override fun openConnection(url: URL?): URLConnection? =
        wrapInstrumentedConnection(
            invokeDelegate { openConnectionMethod.invoke(delegateHandler, url) },
        )

    /**
     * Unwraps reflection exceptions so callers see exactly what [delegateHandler] threw, rather than an undeclared
     * checked [InvocationTargetException] that a `catch (IOException)` would miss.
     */
    private inline fun invokeDelegate(invocation: () -> Any?): URLConnection? {
        try {
            return invocation() as URLConnection?
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        } catch (e: IllegalAccessException) {
            throw IOException("Failed to invoke ${delegateHandler::class.java.simpleName}.openConnection", e)
        }
    }

    /**
     * Return URLConnection with instrumented wrapper if it is an HttpsURLConnection
     */
    private fun wrapInstrumentedConnection(wrappedConnection: URLConnection?): URLConnection? {
        val dataSource = dataSourceProvider()
        return if (wrappedConnection is HttpsURLConnection && dataSource != null) {
            InstrumentedHttpsURLConnection(
                wrappedConnection = wrappedConnection,
                clock = clock,
                hucLiteDataSource = dataSource,
            )
        } else {
            wrappedConnection
        }
    }
}
