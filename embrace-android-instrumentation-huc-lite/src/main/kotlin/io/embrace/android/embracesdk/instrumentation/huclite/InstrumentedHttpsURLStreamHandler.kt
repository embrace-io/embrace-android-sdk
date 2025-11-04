package io.embrace.android.embracesdk.instrumentation.huclite

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import java.io.IOException
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import javax.net.ssl.HttpsURLConnection

/**
 * [URLStreamHandler] that adds an instrumentation wrapper around the [URLConnection] created by [delegatedHandler]. Note that
 * [delegatedHandler] must produce an [HttpsURLConnection], or an exception will be thrown when the connection is opened.
 */
internal class InstrumentedHttpsURLStreamHandler(
    private val delegatedHandler: URLStreamHandler,
    private val clock: Clock,
    private val hucLiteDataSource: HucLiteDataSource,
) : URLStreamHandler() {

    @SuppressLint("PrivateApi")
    override fun openConnection(url: URL?, proxy: Proxy?): URLConnection? {
        try {
            val method =
                findDeclaredMethod(
                    delegatedHandler,
                    delegatedHandler.javaClass,
                    "openConnection",
                    URL::class.java,
                    Proxy::class.java
                )
            method.isAccessible = true
            val httpsConnection = method.invoke(delegatedHandler, url, proxy) as HttpsURLConnection
            return httpsConnection.toWrappedConnection()
        } catch (t: Throwable) {
            throw (t.toInstrumentedConnectionException())
        }
    }

    override fun openConnection(url: URL?): URLConnection? {
        try {
            val method =
                findDeclaredMethod(
                    delegatedHandler,
                    delegatedHandler.javaClass,
                    "openConnection",
                    URL::class.java
                )
            method.isAccessible = true
            val httpsConnection = method.invoke(delegatedHandler, url) as HttpsURLConnection
            return httpsConnection.toWrappedConnection()
        } catch (t: Throwable) {
            throw (t.toInstrumentedConnectionException())
        }
    }

    private fun HttpsURLConnection.toWrappedConnection(): InstrumentedHttpsURLConnection = InstrumentedHttpsURLConnection(
        wrappedConnection = this,
        clock = clock,
        hucLiteDataSource = hucLiteDataSource,
    )

    private fun Throwable.toInstrumentedConnectionException(): IOException =
        InstrumentedConnectionException("Failed to instrumented HTTPS connection", this)

    private class InstrumentedConnectionException(
        override val message: String?,
        override val cause: Throwable?
    ) : IOException(message, cause)
}
