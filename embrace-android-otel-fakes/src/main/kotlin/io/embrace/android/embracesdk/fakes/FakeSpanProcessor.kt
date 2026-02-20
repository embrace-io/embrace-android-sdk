package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

@OptIn(ExperimentalApi::class)
class FakeSpanProcessor(
    private val onStartAction: (ReadWriteSpan) -> Unit = {},
    private val onEndAction: (ReadableSpan) -> Unit = {},
) : SpanProcessor {

    val startedSpanNames = mutableListOf<String>()
    val endedSpanNames = mutableListOf<String>()

    override fun isEndRequired(): Boolean = true
    override fun isStartRequired(): Boolean = true

    override fun onEnding(span: ReadWriteSpan) {
    }

    override fun onEnd(span: ReadableSpan) {
        endedSpanNames.add(span.name)
        onEndAction(span)
    }

    override fun onStart(
        span: ReadWriteSpan,
        parentContext: Context,
    ) {
        startedSpanNames.add(span.name)
        onStartAction(span)
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
