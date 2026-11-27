package io.embrace.android.embracesdk.network

/**
 * Sets the properties of a network request where a HTTP response was returned.
 */
@NetworkRequestDslMarker
public interface CompletedNetworkRequestDsl {

    /**
     * The response status of the request. Must be in the range 100 to 599.
     */
    public var statusCode: Int?

    /**
     * The number of bytes sent.
     */
    public var bytesSent: Long?

    /**
     * The number of bytes received.
     */
    public var bytesReceived: Long?
}
