package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.network.http.HttpRequestInfo
import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * Decides the parent of a captured request's network span before the span starts.
 */
public fun interface NetworkSpanParentProvider {

    /**
     * Returns the parent decision for [request], given the network span parent currently set, if any.
     */
    public fun getNetworkSpanParent(request: HttpRequestInfo, currentParent: SpanContext?): NetworkSpanParent
}
