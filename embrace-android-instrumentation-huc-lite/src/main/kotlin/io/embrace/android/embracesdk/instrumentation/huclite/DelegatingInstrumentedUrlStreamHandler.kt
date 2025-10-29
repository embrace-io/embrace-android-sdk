package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
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
    private val sdkStateApi: SdkStateApi,
    private val instrumentationApi: InstrumentationApi,
    private val networkRequestApi: NetworkRequestApi,
    private val internalInterface: EmbraceInternalInterface,
) : URLStreamHandler() {
    override fun openConnection(url: URL?, proxy: Proxy?): URLConnection? {
        try {
            val method =
                findDeclaredMethod(
                    delegateHandler,
                    delegateHandler.javaClass,
                    "openConnection",
                    URL::class.java,
                    Proxy::class.java
                )
            method.isAccessible = true
            return wrapInstrumentedConnection(method.invoke(delegateHandler, url, proxy) as URLConnection?)
        } catch (t: Throwable) {
            internalInterface.logInternalError(t)
            throw (t)
        }
    }

    override fun openConnection(url: URL?): URLConnection? {
        try {
            val method =
                findDeclaredMethod(
                    delegateHandler,
                    delegateHandler.javaClass,
                    "openConnection",
                    URL::class.java
                )
            method.isAccessible = true
            return wrapInstrumentedConnection(method.invoke(delegateHandler, url) as URLConnection?)
        } catch (t: Throwable) {
            internalInterface.logInternalError(t)
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
                sdkStateApi = sdkStateApi,
                instrumentationApi = instrumentationApi,
                networkRequestApi = networkRequestApi,
                internalInterface = internalInterface
            )
        } else {
            wrappedConnection
        }
    }
}
