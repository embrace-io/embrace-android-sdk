package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.network.http.HttpRequestInfo
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * What a [NetworkSpanParentProvider] is given about a captured request's network span before it starts. You should not implement the
 * `NetworkSpanStartInfo` interface directly in your own custom classes, as new properties may be added in the future.
 */
public interface NetworkSpanStartInfo {

    /**
     * The request the network span is for.
     */
    public val request: HttpRequestInfo

    /**
     * The network span parent currently set, which can be returned as the decision, or null if none is set.
     */
    public val currentParent: NetworkSpanParent?

    /**
     * The span of the current network span parent, or null if none is set or the SDK doesn't track it.
     */
    public val currentParentSpan: EmbraceSpan?

    /**
     * The span context of the current network span parent, or null if none is set.
     */
    public val currentParentSpanContext: SpanContext?
}
