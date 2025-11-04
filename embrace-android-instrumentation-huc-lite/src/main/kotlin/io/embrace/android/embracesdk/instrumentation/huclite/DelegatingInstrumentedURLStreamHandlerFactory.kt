package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
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
    private val logger: EmbLogger,
    private val hucLiteDataSource: HucLiteDataSource,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
        val delegateHandler: URLStreamHandler? = try {
            delegateHandlerFactory.createURLStreamHandler(protocol)
        } catch (t: Throwable) {
            logger.trackInternalError(InternalErrorType.DELEGATING_URL_STREAM_HANDLER_FACTORY_FAIL, t)
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
