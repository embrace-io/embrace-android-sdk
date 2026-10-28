package io.embrace.android.embracesdk.network.http

/**
 * Information captured about an HTTP request by instrumentation. You should not implement the `HttpRequestInfo` interface directly in your
 * own custom classes, as new functions may be added in the future.
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

    /**
     * The HTTP client's own request object, such as an `okhttp3.Request`, or null if the instrumentation doesn't provide one.
     */
    public val nativeRequest: Any?
        get() = null
}
