package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.InternalApi
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.atomic.AtomicLong

/**
 * [SpanProcessor] that adds custom attributes to a [Span] when it starts, and exports it to the given [SpanExporter] when it finishes
 *
 * Note: no explicit tests exist for this as its functionality is tested via the tests for [SpansServiceImpl]
 */
@InternalApi
internal class EmbraceSpanProcessor(private val spanExporter: SpanExporter) : SpanProcessor {

    // TODO: sequence-id should be persisted across cold starts to better gauge data loss
    private val counter = AtomicLong(1)

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        span.setSequenceId(counter.getAndIncrement())
    }

    override fun onEnd(span: ReadableSpan) {
        // TODO: consider exporting this to a buffer that will export the collected Spans in bulk for performance reasons
        spanExporter.export(mutableListOf(span.toSpanData()))
    }

    override fun isStartRequired() = false

    override fun isEndRequired() = true
}
