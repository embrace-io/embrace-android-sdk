package io.embrace.android.embracesdk.network.http

/**
 * Information captured about an HTTP request by instrumentation.
 */
public interface HttpRequestInfo {
    /**
     * The request's URL. Must start with http:// or https://
     */
    public val url: String

    /**
     * The request's method. Must be one of the following: GET, PUT, POST, DELETE, PATCH.
     */
    public val httpMethod: String
}
