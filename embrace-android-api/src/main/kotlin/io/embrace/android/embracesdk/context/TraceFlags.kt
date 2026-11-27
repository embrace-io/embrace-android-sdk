package io.embrace.android.embracesdk.context

/**
 * Details about a trace.
 */
public interface TraceFlags {

    /**
     * True if the trace is sampled.
     */
    public val isSampled: Boolean

    /**
     * True if the trace ID is random.
     */
    public val isRandom: Boolean
}
