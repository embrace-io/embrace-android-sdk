package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

/**
 * [URLStreamHandlerFactory] that only supports HTTPS that creates instances of [InstrumentedHttpsURLStreamHandler]
 */
internal class InstrumentedUrlStreamHandlerFactory(
    private val httpsHandler: URLStreamHandler,
    private val sdkStateApi: SdkStateApi,
    private val instrumentationApi: InstrumentationApi,
    private val networkRequestApi: NetworkRequestApi,
    private val internalInterface: EmbraceInternalInterface,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): InstrumentedHttpsURLStreamHandler? {
        return if (protocol?.lowercase() == "https") {
            InstrumentedHttpsURLStreamHandler(
                delegatedHandler = httpsHandler,
                sdkStateApi = sdkStateApi,
                instrumentationApi = instrumentationApi,
                networkRequestApi = networkRequestApi,
                internalInterface = internalInterface
            )
        } else {
            null
        }
    }
}
