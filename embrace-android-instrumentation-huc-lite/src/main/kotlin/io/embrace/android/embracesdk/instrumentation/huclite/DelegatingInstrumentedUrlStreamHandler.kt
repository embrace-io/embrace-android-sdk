package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
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
    private val hucLiteDataSource: HucLiteDataSource,
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

    override fun openConnection(url: URL?, proxy: Proxy?): URLConnection? {
        try {
            return wrapInstrumentedConnection(
                openProxyConnectionMethod.invoke(delegateHandler, url, proxy) as URLConnection?,
            )
        } catch (t: Throwable) {
            throw (t)
        }
    }

    override fun openConnection(url: URL?): URLConnection? {
        try {
            return wrapInstrumentedConnection(
                openConnectionMethod.invoke(delegateHandler, url) as URLConnection?,
            )
        } catch (t: Throwable) {
            throw (t)
        }
    }

    /**
     * Return URLConnection with instrumented wrapper if it is an HttpsURLConnection
     */
    private fun wrapInstrumentedConnection(wrappedConnection: URLConnection?): URLConnection? {
        return if (wrappedConnection is HttpsURLConnection) {
            InstrumentedHttpsURLConnection(
                wrappedConnection = wrappedConnection,
                clock = clock,
                hucLiteDataSource = hucLiteDataSource,
            )
        } else {
            wrappedConnection
        }
    }
}
