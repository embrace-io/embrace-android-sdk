package io.embrace.android.embracesdk.internal.instrumentation.network

/**
 * Data used for instrumentation of the execution of an HTTP network request
 */
data class RequestStartData(
    /**
     * URL used to track the request in instrumentation, not necessarily the exact URL that was executed
     */
    val url: String,
    /**
     * HTTP method of the request
     */
    val httpMethod: String,
    /**
     * Timestamp normalized to the SDK clock for the start of the execution of the request (milliseconds)
     */
    val sdkClockStartTime: Long,
    /**
     * The raw value of the traceparent header found in the request
     */
    val traceparent: String? = null,
    /**
     * Duration in milliseconds after which the in-flight span for this request should be considered
     * leaked and dropped. Resolved from the HTTP client's own call timeout where available, otherwise
     * a locally configurable fallback. Null if no timeout could be resolved.
     */
    val timeoutMs: Long? = null,
)
