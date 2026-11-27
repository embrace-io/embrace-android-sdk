package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.network.NetworkRequestDsl
import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.embrace.android.embracesdk.network.http.HttpMethod
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
     * @param url the request's URL. Must start with http:// or https://
     * @param httpMethod the request's HTTP method.
     * @param startTime the time the request started.
     * @param endTime the time the request ended. Must be greater than [startTime].
     * @param action sets the request's optional properties, such as its status code or error.
     */
    public fun recordNetworkRequest(
        url: String,
        httpMethod: HttpMethod,
        startTime: Long,
        endTime: Long,
        action: NetworkRequestDsl.() -> Unit = {},
    )

    /**
     * Logs the fact that a network call occurred. These are recorded and sent to Embrace as part
     * of a session part.
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        "Use recordNetworkRequest(url, httpMethod, startTime, endTime) { completed { ... } } or " +
            "{ incomplete { ... } } instead.",
    )
    public fun recordNetworkRequest(networkRequest: io.embrace.android.embracesdk.network.EmbraceNetworkRequest) {
        recordNetworkRequest(
            networkRequest.url,
            HttpMethod.fromString(networkRequest.httpMethod),
            networkRequest.startTime,
            networkRequest.endTime,
        ) {
            traceId = networkRequest.traceId
            w3cTraceparent = networkRequest.w3cTraceparent
            networkCaptureData = networkRequest.networkCaptureData
            val responseCode = networkRequest.responseCode
            if (responseCode != null) {
                completed {
                    statusCode = responseCode
                    bytesSent = networkRequest.bytesSent
                    bytesReceived = networkRequest.bytesReceived
                }
            } else {
                incomplete {
                    errorType = networkRequest.errorType
                    errorMessage = networkRequest.errorMessage
                }
            }
        }
    }

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
     * Clears the network span parent and returns `true` if [token] is from the most recent set, otherwise does nothing and returns `false`.
     */
    public fun clearNetworkSpanParent(token: NetworkSpanParentToken): Boolean

    /**
     * Clears the network span parent unconditionally.
     */
    public fun clearNetworkSpanParent()

    /**
     * Registers the provider consulted for the network span parent of each request captured after this call, replacing any previous
     * one; it must be called after start.
     */
    public fun setNetworkSpanParentProvider(provider: NetworkSpanParentProvider)

    @Deprecated("This is no longer supported")
    public fun generateW3cTraceparent(): String?
}
