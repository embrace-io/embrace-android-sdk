package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

/**
 * [URLStreamHandlerFactory] that delegates the creation of [URLStreamHandler] to [delegateHandlerFactory], and wraps the resulting handler
 * with [DelegatingInstrumentedUrlStreamHandler] so the created connection is instrumented if appropriate.
 *
 * If [delegateHandlerFactory] doesn't support the protocol, the [instrumentedHandlerFactory] will be used instead.
 */
internal class DelegatingInstrumentedURLStreamHandlerFactory(
    private val delegateHandlerFactory: URLStreamHandlerFactory,
    private val instrumentedHandlerFactory: () -> InstrumentedUrlStreamHandlerFactory,
    private val clock: Clock,
    private val hucLiteDataSource: HucLiteDataSource,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
        val delegateHandler: URLStreamHandler? = try {
            delegateHandlerFactory.createURLStreamHandler(protocol)
        } catch (_: Throwable) {
            null
        }

        return if (delegateHandler != null) {
            DelegatingInstrumentedUrlStreamHandler(
                delegateHandler = delegateHandler,
                clock = clock,
                hucLiteDataSource = hucLiteDataSource,
            )
        } else {
            instrumentedHandlerFactory().createURLStreamHandler(protocol)
        }
    }
}
