package io.embrace.android.embracesdk.internal.instrumentation.network

/**
 * A network request whose span has started, with the traceparent header to send with it.
 */
class RequestStart(
    /**
     * Identifies the request in later calls to [NetworkRequestDataSource].
     */
    val id: String,
    /**
     * The traceparent header to send with the request when injection applies to its host, or null to leave the header unchanged.
     */
    val traceparent: String?,
)
