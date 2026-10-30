package io.embrace.android.embracesdk.network

import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * The parent decision that a [NetworkSpanParentProvider] returns for one request.
 */
public class NetworkSpanParent private constructor(

    /**
     * The span context to parent the network span to, or null for [NO_PARENT] and [USE_DEFAULT].
     */
    public val spanContext: SpanContext?,
) {

    public companion object {

        /**
         * Makes the network span a root span, overriding the network span parent and any traceparent header.
         */
        @JvmField
        public val NO_PARENT: NetworkSpanParent = NetworkSpanParent(null)

        /**
         * Applies the default rules as if no provider were set.
         */
        @JvmField
        public val USE_DEFAULT: NetworkSpanParent = NetworkSpanParent(null)

        /**
         * Parents the network span to [spanContext].
         */
        @JvmStatic
        public fun parent(spanContext: SpanContext): NetworkSpanParent = NetworkSpanParent(spanContext)
    }
}
