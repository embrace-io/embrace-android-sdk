package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
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
    private val sdkStateApi: SdkStateApi,
    private val instrumentationApi: InstrumentationApi,
    private val networkRequestApi: NetworkRequestApi,
    private val internalInterface: EmbraceInternalInterface,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
        val delegateHandler: URLStreamHandler? = try {
            delegateHandlerFactory.createURLStreamHandler(protocol)
        } catch (t: Throwable) {
            internalInterface.logInternalError(t)
            null
        }

        return if (delegateHandler != null) {
            DelegatingInstrumentedUrlStreamHandler(
                delegateHandler = delegateHandler,
                sdkStateApi = sdkStateApi,
                instrumentationApi = instrumentationApi,
                networkRequestApi = networkRequestApi,
                internalInterface = internalInterface
            )
        } else {
            instrumentedHandlerFactory().createURLStreamHandler(protocol)
        }
    }
}
