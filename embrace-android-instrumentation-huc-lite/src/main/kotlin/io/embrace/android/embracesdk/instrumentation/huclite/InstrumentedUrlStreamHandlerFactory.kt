package io.embrace.android.embracesdk.instrumentation.huclite

import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

/**
 * [URLStreamHandlerFactory] that only supports HTTPS that creates instances of [InstrumentedHttpsURLStreamHandler]
 */
internal class InstrumentedUrlStreamHandlerFactory(
    private val httpsHandler: URLStreamHandler,
    private val errorHandler: (Throwable) -> Unit,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): InstrumentedHttpsURLStreamHandler? {
        return if (protocol?.lowercase() == "https") {
            InstrumentedHttpsURLStreamHandler(httpsHandler, errorHandler)
        } else {
            null
        }
    }
}
