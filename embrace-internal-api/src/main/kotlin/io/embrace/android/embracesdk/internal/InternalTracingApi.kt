package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * Internal interface for hosted SDKs and access the tracing API
 */
interface InternalTracingApi {

    /**
     * Create and start a new span. Returns the spanId of the new span if both operations are successful, and null if either fails.
     */
    fun startSpan(
        name: String,
        parentSpanId: String? = null,
        startTimeMs: Long? = null,
    ): String?

    /**
     * Stop an active span. Returns true if the span is stopped after the method returns and false otherwise.
     */
    fun stopSpan(
        spanId: String,
        errorCode: ErrorCode? = null,
        endTimeMs: Long? = null,
    ): Boolean

    /**
     * Create and add a Span Event with the given parameters to an active span with the given [spanId]. Returns false if the event
     * cannot be added.
     */
    fun addSpanEvent(
        spanId: String,
        name: String,
        timestampMs: Long? = null,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean

    /**
     * Add an attribute to an active span with the given [spanId]. Returns true if the attributed is added and false otherwise.
     */
    fun addSpanAttribute(
        spanId: String,
        key: String,
        value: String,
    ): Boolean

    /**
     * Record a span around the execution of the given lambda. If an uncaught exception occurs during the execution, the span will be
     * terminated as a failure.
     *
     * The map representing an event has the following schema:
     *
     * {
     *  "name": [String],
     *  "timestampMs": [Long] (optional),
     *  "timestampNanos": [Long] (deprecated and optional),
     *  "attributes": [Map<String, String>] (optional)
     * }
     *
     * Any object passed in the list that violates that schema will be dropped and no event will be created for it. If an entry in the
     * attributes map isn't <String, String>, it'll also be dropped. Omitting or passing in nulls for the optional fields are OK.
     */
    fun <T> recordSpan(
        name: String,
        parentSpanId: String? = null,
        attributes: Map<String, String> = emptyMap(),
        events: List<Map<String, Any>> = emptyList(),
        code: () -> T,
    ): T

    /**
     * Record a completed span with the given parameters. Returns true if the span is record and false otherwise.
     */
    fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode? = null,
        parentSpanId: String? = null,
        attributes: Map<String, String> = emptyMap(),
        events: List<Map<String, Any>> = emptyList(),
    ): Boolean
}
