package io.embrace.android.embracesdk.network

/**
 * Decides the parent of a captured request's network span before the span starts.
 */
public fun interface NetworkSpanParentProvider {

    /**
     * Returns the parent decision for the request in [info].
     */
    public fun getNetworkSpanParent(info: NetworkSpanStartInfo): NetworkSpanParent
}
