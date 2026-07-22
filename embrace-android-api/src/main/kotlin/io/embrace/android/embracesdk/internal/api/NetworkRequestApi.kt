package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier

/**
 * The public API that is used for capturing network requests manually
 */
@InternalApi
public interface NetworkRequestApi {

    /**
     * Logs the fact that a network call occurred. These are recorded and sent to Embrace as part
     * of a session part.
     *
     * You can create an instance of [EmbraceNetworkRequest] using the factory functions.
     */
    public fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest)

    /**
     * Registers a [HttpRequestInfoModifier] that will be invoked to alter the HTTP request info
     * captured by network instrumentation before it is reported as telemetry. This does not modify
     * the underlying HTTP request that is executed.
     */
    public fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier)

    /**
     * Unregisters a [HttpRequestInfoModifier] that was previously registered via
     * [addHttpRequestInfoModifier].
     */
    public fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier)

    @Deprecated("This is no longer supported")
    public fun generateW3cTraceparent(): String?
}
