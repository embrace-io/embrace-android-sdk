package io.embrace.android.embracesdk.internal.otel.state

/**
 * What a span the SDK makes tells, and asks of, whatever links spans to state. The OpenTelemetry span says the rest
 * itself, to a processor.
 */
interface SpanStateLink {

    /**
     * The number of the last state change before the span with the id [spanId] started, while it is in progress. Null if
     * the span is not linked, or no state had a value when it started.
     */
    fun startSequence(spanId: String): Long?

    /**
     * The span with the id [spanId] is about to be ended at [endTimeMs], a time it was given, in milliseconds by the SDK's
     * clock. To be called immediately before the OpenTelemetry span is ended, on the same thread.
     */
    fun onEndTimeGiven(spanId: String, endTimeMs: Long)

    /**
     * The span with the id [spanId] was discarded: it will never end, so anything kept for it until it does is let go.
     */
    fun onDiscarded(spanId: String)
}
