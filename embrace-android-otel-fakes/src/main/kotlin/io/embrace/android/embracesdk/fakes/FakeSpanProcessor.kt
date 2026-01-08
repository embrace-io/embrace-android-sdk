package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.embrace.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan

@OptIn(ExperimentalApi::class)
class FakeSpanProcessor(
    private val onStartAction: (ReadWriteSpan) -> Unit = {},
    private val onEndAction: (ReadableSpan) -> Unit = {},
) : SpanProcessor {

    val startedSpanNames = mutableListOf<String>()
    val endedSpanNames = mutableListOf<String>()

    override fun isEndRequired(): Boolean = true
    override fun isStartRequired(): Boolean = true

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

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
