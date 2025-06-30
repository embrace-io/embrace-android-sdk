package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embSequenceId
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.embrace.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan
import java.util.concurrent.atomic.AtomicLong

/**
 * Default processor that adds custom attributes to a span when it starts and
 * exports it to the supplied exporter when it finishes
 */
@OptIn(ExperimentalApi::class)
class DefaultSpanProcessor(
    private val spanExporter: SpanExporter,
    private val processIdentifier: String,
) : SpanProcessor {

    private val counter = AtomicLong(1)

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        span.setStringAttribute(embSequenceId.name, counter.getAndIncrement().toString())
        span.setStringAttribute(embProcessIdentifier.name, processIdentifier)
    }

    override fun onEnd(span: ReadableSpan) {
        spanExporter.export(mutableListOf(span))
    }

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
