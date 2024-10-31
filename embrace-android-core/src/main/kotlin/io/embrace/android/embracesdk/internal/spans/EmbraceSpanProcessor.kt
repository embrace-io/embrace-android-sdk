package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.internal.opentelemetry.embSequenceId
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.atomic.AtomicLong

/**
 * [SpanProcessor] that adds custom attributes to a [Span] when it starts, and exports it to the given [SpanExporter] when it finishes
 */
internal class EmbraceSpanProcessor(
    private val spanExporter: SpanExporter,
    private val processIdentifier: String,
) : SpanProcessor {

    private val counter = AtomicLong(1)

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        span.setEmbraceAttribute(embSequenceId, counter.getAndIncrement().toString())
        span.setEmbraceAttribute(embProcessIdentifier, processIdentifier)
    }

    override fun onEnd(span: ReadableSpan) {
        spanExporter.export(mutableListOf(span.toSpanData()))
    }

    override fun isStartRequired() = true

    override fun isEndRequired() = true
}
