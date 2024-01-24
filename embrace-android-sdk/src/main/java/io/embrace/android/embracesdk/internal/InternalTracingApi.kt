package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * Internal interface for hosted SDKs and access the tracing API
 */
@InternalApi
public interface InternalTracingApi {

    /**
     * Create and start a new span. Returns the spanId of the new span if both operations are successful, and null if either fails.
     */
    public fun startSpan(
        name: String,
        parentSpanId: String? = null
    ): String?

    /**
     * Stop an active span. Returns true if the span is stopped after the method returns and false otherwise.
     */
    public fun stopSpan(
        spanId: String,
        errorCode: ErrorCode? = null
    ): Boolean

    /**
     * Create and add a Span Event with the given parameters to an active span with the given [spanId]. Returns false if the event
     * cannot be added.
     */
    public fun addSpanEvent(
        spanId: String,
        name: String,
        time: Long? = null,
        attributes: Map<String, String>? = null
    ): Boolean

    /**
     * Add an attribute to an active span with the given [spanId]. Returns true if the attributed is added and false otherwise.
     */
    public fun addSpanAttribute(
        spanId: String,
        key: String,
        value: String
    ): Boolean

    /**
     * Record a span around the execution of the given lambda. If an uncaught exception occurs during the execution, the span will be
     * terminated as a failure.
     */
    public fun <T> recordSpan(
        name: String,
        parentSpanId: String? = null,
        attributes: Map<String, String>? = null,
        events: List<Map<String, Any>>? = null,
        code: () -> T
    ): T

    /**
     * Record a completed span with the given parameters. Returns true if the span is record and false otherwise.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode? = null,
        parentSpanId: String? = null,
        attributes: Map<String, String>? = null,
        events: List<Map<String, Any>>? = null
    ): Boolean
}
