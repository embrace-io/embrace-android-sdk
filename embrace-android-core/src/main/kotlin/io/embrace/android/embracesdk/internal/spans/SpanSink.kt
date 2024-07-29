package io.embrace.android.embracesdk.internal.spans

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * A service that stores all the spans that are completed and exported via [EmbraceSpanExporter], and provides access to them so they
 * can be sent off-device at the appropriate cadence.
 */
public interface SpanSink {
    /**
     * Stores spans that have been completed. Implementations must support concurrent invocations.
     */
    public fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode

    /**
     * Returns the list of the currently stored completed spans.
     */
    public fun completedSpans(): List<EmbraceSpanData>

    /**
     * Returns and clears the currently stored completed Spans. Implementations of this method must make sure the clearing and returning is
     * atomic, i.e. spans cannot be added during this operation.
     */
    public fun flushSpans(): List<EmbraceSpanData>
}
