package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.arch.attrs.embSequenceId
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.embrace.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalApi::class)
class EmbraceSpanProcessor(
    private val sessionIdProvider: () -> String?,
    private val processIdentifier: String,
    private val spanExporter: SpanExporter,
) : SpanProcessor {

    private val counter = AtomicLong(1)

    @OptIn(IncubatingApi::class)
    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        span.setStringAttribute(embSequenceId.name, counter.getAndIncrement().toString())
        span.setStringAttribute(embProcessIdentifier.name, processIdentifier)
        sessionIdProvider()?.let { sessionId ->
            span.setStringAttribute(SessionAttributes.SESSION_ID, sessionId)
        }
    }

    override fun onEnd(span: ReadableSpan) {
        spanExporter.export(mutableListOf(span))
    }

    override fun isStartRequired() = true
    override fun isEndRequired() = true
    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
