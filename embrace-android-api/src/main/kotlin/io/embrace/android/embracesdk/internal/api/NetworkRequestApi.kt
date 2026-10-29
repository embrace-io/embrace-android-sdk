package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.tracing.SpanContext

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
     * the underlying HTTP request that is executed. Only requests recorded after this method returns
     * will be modified by the given [modifier].
     */
    public fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier)

    /**
     * Unregisters a [HttpRequestInfoModifier] that was previously registered via
     * [addHttpRequestInfoModifier].
     */
    public fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier)

    /**
     * Sets the span that network spans started from now on are parented to, returning a token that clears only this set.
     */
    public fun setNetworkSpanParent(span: EmbraceSpan): NetworkSpanParentToken

    /**
     * Sets the span context that network spans started from now on are parented to, returning a token that clears only this set.
     */
    public fun setNetworkSpanParent(spanContext: SpanContext): NetworkSpanParentToken

    /**
     * Clears the network span parent if [token] is from the most recent set, otherwise does nothing.
     */
    public fun clearNetworkSpanParent(token: NetworkSpanParentToken)

    /**
     * Registers the provider consulted for the network span parent of each request captured after this call, replacing any previous one.
     */
    public fun setNetworkSpanParentProvider(provider: NetworkSpanParentProvider)

    @Deprecated("This is no longer supported")
    public fun generateW3cTraceparent(): String?
}
