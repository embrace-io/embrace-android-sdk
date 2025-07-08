package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embSequenceId
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.embrace.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalApi::class)
class EmbraceSpanProcessor(
    private val sessionIdProvider: () -> String?,
    private val processIdentifier: String,
) : SpanProcessor {

    private val counter = AtomicLong(1)

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        span.setStringAttribute(embSequenceId.name, counter.getAndIncrement().toString())
        span.setStringAttribute(embProcessIdentifier.name, processIdentifier)
        sessionIdProvider()?.let { sessionId ->
            span.setStringAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)
        }
    }

    override fun onEnd(span: ReadableSpan) {
    }

    override fun isStartRequired() = true
    override fun isEndRequired() = true
    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
