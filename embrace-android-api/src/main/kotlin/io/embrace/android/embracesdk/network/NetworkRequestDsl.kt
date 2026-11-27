package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData

/**
 * Sets the optional properties of a network request recorded with
 * [io.embrace.android.embracesdk.internal.api.NetworkRequestApi.recordNetworkRequest].
 *
 * Call [completed] when a HTTP response was returned, or [incomplete] when the request failed without one,
 * e.g. due to a connection error. Only the last of these calls is kept.
 */
@NetworkRequestDslMarker
public interface NetworkRequestDsl {

    /**
     * Optional trace ID that can be used to trace a particular request. Max length is 64 characters.
     */
    public var traceId: String?

    /**
     * Optional W3C-compliant traceparent representing the network call that is being recorded.
     */
    public var w3cTraceparent: String?

    /**
     * Network capture data for the request.
     */
    public var networkCaptureData: NetworkCaptureData?

    /**
     * Marks the request as one where a HTTP response was returned.
     */
    public fun completed(action: CompletedNetworkRequestDsl.() -> Unit)

    /**
     * Marks the request as one where no HTTP response was returned, e.g. due to a connection error.
     */
    public fun incomplete(action: IncompleteNetworkRequestDsl.() -> Unit)
}
