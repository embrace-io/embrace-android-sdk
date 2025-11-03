package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

/**
 * [URLStreamHandlerFactory] that only supports HTTPS that creates instances of [InstrumentedHttpsURLStreamHandler]
 */
internal class InstrumentedUrlStreamHandlerFactory(
    private val httpsHandler: URLStreamHandler,
    private val clock: Clock,
    private val hucLiteDataSource: HucLiteDataSource,
    private val errorHandler: (Throwable) -> Unit,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): InstrumentedHttpsURLStreamHandler? {
        return if (protocol?.lowercase() == "https") {
            InstrumentedHttpsURLStreamHandler(
                delegatedHandler = httpsHandler,
                clock = clock,
                hucLiteDataSource = hucLiteDataSource,
                errorHandler = errorHandler,
            )
        } else {
            null
        }
    }
}
