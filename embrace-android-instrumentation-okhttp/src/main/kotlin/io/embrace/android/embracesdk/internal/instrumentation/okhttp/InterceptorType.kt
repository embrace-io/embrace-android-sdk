package io.embrace.android.embracesdk.internal.instrumentation.okhttp

/**
 * Enumerates the interceptor type (application or network)
 */
internal enum class InterceptorType {

    /**
     * An interceptor that within the Embrace instrumentation only intercepts errors the client app experiences.
     *
     * We use OkHttp application interceptor in this case because this interceptor
     * will be added first in the OkHttp3 interceptors stack. This allows us to catch network errors.
     * OkHttp network interceptors are added almost at the end of stack, they are closer to "the wire"
     * so they are not able to see network errors.
     *
     * We use the [EmbraceCustomPathException] to capture the custom path added in the interceptor
     * chain process for client errors on requests to a generic URL like a GraphQL endpoint.
     */
    APPLICATION,

    /**
     * An interceptor that within the Embrace instrumentation captures the results of the network call
     * as telemetry.
     *
     * This interceptor will only intercept network requests and responses from client app.
     * OkHttp network interceptors are added almost at the end of stack, they are closer to "Wire"
     * so they are able to see catch "real requests".
     *
     * Network Interceptors
     * - Able to operate on intermediate responses like redirects and retries.
     * - Not invoked for cached responses that short-circuit the network.
     * - Observe the data just as it will be transmitted over the network.
     * - Access to the Connection that carries the request.
     */
    NETWORK,
}
