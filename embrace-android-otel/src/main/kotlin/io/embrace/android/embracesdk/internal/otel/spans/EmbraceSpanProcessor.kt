package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embSequenceId
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadWriteSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadableSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanProcessor
import java.util.concurrent.atomic.AtomicLong

/**
 * [SpanProcessor] that adds custom attributes to a [Span] when it starts, and exports it to the given [SpanExporter] when it finishes
 */
class EmbraceSpanProcessor(
    private val spanExporter: OtelJavaSpanExporter,
    private val processIdentifier: String,
) : OtelJavaSpanProcessor {

    private val counter = AtomicLong(1)

    override fun onStart(parentContext: OtelJavaContext, span: OtelJavaReadWriteSpan) {
        span.setEmbraceAttribute(embSequenceId, counter.getAndIncrement().toString())
        span.setEmbraceAttribute(embProcessIdentifier, processIdentifier)
    }

    override fun onEnd(span: OtelJavaReadableSpan) {
        spanExporter.export(mutableListOf(span.toSpanData()))
    }

    override fun isStartRequired() = true

    override fun isEndRequired() = true
}
