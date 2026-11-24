package io.embrace.android.embracesdk.internal.otel.state

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

/**
 * Wraps a [SpanProcessor] supplied by the SDK user so that every linked span it sees carries the value of every state
 * when the span started, and of each state that differed when it ended. The span itself is not written to, so none of
 * that reaches Embrace's own payload.
 */
internal class StateDecoratingSpanProcessor(
    private val delegate: SpanProcessor,
    private val tracker: SpanStateTracker,
) : SpanProcessor by delegate {

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        delegate.onStart(tracker.withValuesWhileLive(span), parentContext)
    }

    override fun onEnding(span: ReadWriteSpan) {
        delegate.onEnding(tracker.withValuesWhileLive(span))
    }

    override fun onEnd(span: ReadableSpan) {
        delegate.onEnd(tracker.withValuesAtEnd(span))
    }
}

/**
 * Forgets what [tracker] kept for a span once the span has ended. It has to be the last processor to see a span end, as
 * the ones before it read what is kept.
 */
internal class SpanStateReleaseProcessor(
    private val tracker: SpanStateTracker,
) : SpanProcessor {

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
    }

    override fun onEnding(span: ReadWriteSpan) {
    }

    override fun onEnd(span: ReadableSpan) {
        tracker.release(span.spanContext.spanId)
    }

    override fun isStartRequired(): Boolean = false
    override fun isEndRequired(): Boolean = true
    override fun isOnEndingRequired(): Boolean = false
    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
