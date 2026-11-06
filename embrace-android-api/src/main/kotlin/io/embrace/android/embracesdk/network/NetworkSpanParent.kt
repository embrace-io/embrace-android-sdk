package io.embrace.android.embracesdk.network

import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * The parent decision that a [NetworkSpanParentProvider] returns for one request.
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
    }
}

private class NetworkSpanParentImpl(
    override val traceId: String?,
    override val spanId: String?,
) : NetworkSpanParent
