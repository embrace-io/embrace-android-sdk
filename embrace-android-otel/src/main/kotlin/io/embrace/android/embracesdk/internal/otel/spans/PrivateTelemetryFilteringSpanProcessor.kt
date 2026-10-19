package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

/**
 * Wraps a [SpanProcessor] supplied by the SDK user so that it never sees spans that are private to Embrace,
 * and sees nothing at all once [exportCheck] fails.
 */
internal class PrivateTelemetryFilteringSpanProcessor(
    private val delegate: SpanProcessor,
    private val exportCheck: () -> Boolean,
) : SpanProcessor by delegate {

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        if (shouldForward(span)) {
            delegate.onStart(span, parentContext)
        }
    }

    override fun onEnding(span: ReadWriteSpan) {
        if (shouldForward(span)) {
            delegate.onEnding(span)
        }
    }

    override fun onEnd(span: ReadableSpan) {
        if (shouldForward(span)) {
            delegate.onEnd(span)
        }
    }

    private fun shouldForward(span: ReadableSpan): Boolean = exportCheck() && !span.attributes.containsKey(PrivateSpan.key)
}
