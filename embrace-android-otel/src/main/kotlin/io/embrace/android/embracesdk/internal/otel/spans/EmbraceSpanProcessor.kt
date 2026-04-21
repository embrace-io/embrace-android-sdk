package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.semconv.SessionAttributes
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

class EmbraceSpanProcessor(
    private val sessionIdProvider: () -> SessionIdProvider?,
    private val processIdentifier: String,
    private val spanExporter: SpanExporter,
) : SpanProcessor {

    private val counter = AtomicLong(1)

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        span.setStringAttribute(EmbSessionAttributes.EMB_PRIVATE_SEQUENCE_ID, counter.getAndIncrement().toString())
        span.setStringAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, processIdentifier)
        sessionIdProvider()?.let { provider ->
            provider.getCurrentUserSessionId()?.let { userSessionId ->
                span.setStringAttribute(SessionAttributes.SESSION_ID, userSessionId)
                span.setStringAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
            }
            provider.getCurrentSessionPartId()?.let { sessionPartId ->
                span.setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionPartId)
            }
        }
    }

    override fun onEnding(span: ReadWriteSpan) {
    }

    override fun onEnd(span: ReadableSpan) {
        runBlocking { spanExporter.export(mutableListOf(span)) }
    }

    override fun isStartRequired() = true
    override fun isEndRequired() = true
    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
