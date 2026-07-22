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
     * The request's method (`GET`, `PUT`, `POST`, `DELETE`, `PATCH`, etc.).
     */
    public val httpMethod: String
}
