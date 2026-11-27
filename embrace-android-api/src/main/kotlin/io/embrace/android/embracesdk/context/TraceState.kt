package io.embrace.android.embracesdk.context

/**
 * Immutable vendor-specific key-value pairs that describe a trace.
 */
public interface TraceState {

    /**
     * Returns the value for [key], or null if it is not present.
     */
    public fun get(key: String): String?

    /**
     * Returns the trace state as a map.
     */
    public fun asMap(): Map<String, String>
}
