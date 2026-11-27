package io.embrace.android.embracesdk.network

/**
 * Sets the properties of a network request where no HTTP response was returned, e.g. due to a connection error.
 */
@NetworkRequestDslMarker
public interface IncompleteNetworkRequestDsl {

    /**
     * Error type that describes a non-HTTP error, e.g. a connection error.
     */
    public var errorType: String?

    /**
     * Error message that describes a non-HTTP error, e.g. a connection error.
     */
    public var errorMessage: String?
}
