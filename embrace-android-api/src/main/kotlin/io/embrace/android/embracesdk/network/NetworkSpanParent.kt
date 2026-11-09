package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * The parent decision that a [NetworkSpanParentProvider] returns for one request. You should not implement the `NetworkSpanParent`
 * interface directly in your own custom classes, as new properties may be added in the future; use the `parent` functions instead.
 */
public interface NetworkSpanParent {

    /**
     * The trace ID of the span to parent the network span to, or null for [NO_PARENT] and [USE_DEFAULT].
     */
    public val traceId: String?

    /**
     * The span ID of the span to parent the network span to, or null for [NO_PARENT] and [USE_DEFAULT].
     */
    public val spanId: String?

    public companion object {

        /**
         * Makes the network span a root span, overriding the network span parent and any traceparent header.
         */
        @JvmField
        public val NO_PARENT: NetworkSpanParent = NetworkSpanParentImpl(null, null)

        /**
         * Applies the default rules as if no provider were set.
         */
        @JvmField
        public val USE_DEFAULT: NetworkSpanParent = NetworkSpanParentImpl(null, null)

        /**
         * Parents the network span to [spanContext].
         */
        @JvmStatic
        public fun parent(spanContext: SpanContext): NetworkSpanParent =
            NetworkSpanParentImpl(spanContext.traceId, spanContext.spanId)

        /**
         * Parents the network span to [span].
         */
        @JvmStatic
        public fun parent(span: EmbraceSpan): NetworkSpanParent =
            NetworkSpanParentImpl(span.traceId, span.spanId)

        /**
         * Parents the network span to the span with [traceId] and [spanId].
         */
        @JvmStatic
        public fun parent(traceId: String, spanId: String): NetworkSpanParent =
            NetworkSpanParentImpl(traceId, spanId)
    }
}

private class NetworkSpanParentImpl(
    override val traceId: String?,
    override val spanId: String?,
) : NetworkSpanParent
