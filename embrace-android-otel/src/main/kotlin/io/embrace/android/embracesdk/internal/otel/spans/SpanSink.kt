package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult

/**
 * A service that stores all the spans that are completed and exported via an exporter,
 * and provides access to them so they
 * can be sent off-device at the appropriate cadence.
 */
interface SpanSink {
    /**
     * Stores spans that have been completed. Implementations must support concurrent invocations.
     */
    fun storeCompletedSpans(spans: List<EmbraceSpanData>): StoreDataResult

    /**
     * Returns the list of the currently stored completed spans.
     */
    fun completedSpans(): List<EmbraceSpanData>

    /**
     * Returns and clears the currently stored completed Spans. Implementations of this method must
     * make sure the clearing and returning is
     * atomic, i.e. spans cannot be added during this operation.
     */
    fun flushSpans(): List<EmbraceSpanData>
}
