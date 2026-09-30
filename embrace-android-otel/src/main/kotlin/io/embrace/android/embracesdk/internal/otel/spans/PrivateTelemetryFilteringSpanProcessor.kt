package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

/**
 * Wraps a [SpanProcessor] supplied by the SDK user so that it never sees spans that are private to Embrace.
 */
internal class PrivateTelemetryFilteringSpanProcessor(
    private val delegate: SpanProcessor,
) : SpanProcessor by delegate {

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        if (!span.isPrivate()) {
            delegate.onStart(span, parentContext)
        }
    }

    override fun onEnding(span: ReadWriteSpan) {
        if (!span.isPrivate()) {
            delegate.onEnding(span)
        }
    }

    override fun onEnd(span: ReadableSpan) {
        if (!span.isPrivate()) {
            delegate.onEnd(span)
        }
    }

    private fun ReadableSpan.isPrivate(): Boolean = attributes.containsKey(PrivateSpan.key)
}
