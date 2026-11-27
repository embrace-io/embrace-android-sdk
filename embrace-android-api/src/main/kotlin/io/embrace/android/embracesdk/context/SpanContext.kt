package io.embrace.android.embracesdk.context

/**
 * The state of a span that is propagated along a distributed trace: a trace ID, a span ID, trace flags and trace state.
 */
public interface SpanContext {

    /**
     * Hexadecimal representation of the trace ID.
     */
    public val traceId: String

    /**
     * The trace ID as a byte array.
     */
    public val traceIdBytes: ByteArray

    /**
     * Hexadecimal representation of the span ID.
     */
    public val spanId: String

    /**
     * The span ID as a byte array.
     */
    public val spanIdBytes: ByteArray

    /**
     * Details about the trace.
     */
    public val traceFlags: TraceFlags

    /**
     * True if the trace ID and span ID are valid.
     */
    public val isValid: Boolean

    /**
     * True if the context was propagated from a remote parent.
     */
    public val isRemote: Boolean

    /**
     * Vendor-specific information about the trace.
     */
    public val traceState: TraceState
}
