package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.semconv.UserAttributes
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

internal class EmbraceSpanProcessor(
    private val sessionIdsProvider: () -> SessionIdsProvider?,
    private val userIdProvider: () -> String? = { null },
    private val processIdentifier: String,
    private val spanExporter: InlineExporter<SpanData>,
) : SpanProcessor {

    override fun onStart(span: ReadWriteSpan, parentContext: Context) {
        span.setStringAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, processIdentifier)
        sessionIdsProvider()?.let { provider ->
            val ids = provider.getActiveSessionIds()
            span.setStringAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, ids.sessionPartId)
            span.setStringAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, ids.userSessionId)
        }
        userIdProvider()?.let { userId ->
            span.setStringAttribute(UserAttributes.USER_ID, userId)
        }
    }

    override fun onEnding(span: ReadWriteSpan) {
    }

    override fun onEnd(span: ReadableSpan) {
        spanExporter.exportInline(listOf(span))
    }

    override fun isStartRequired() = true
    override fun isEndRequired() = true
    override suspend fun forceFlush(): OperationResultCode = spanExporter.forceFlush()
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
