package io.embrace.android.embracesdk.fakes

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

class FakeOtelJavaSpanProcessor : SpanProcessor {

    val startedSpanNames = mutableListOf<String>()
    val endedSpanNames = mutableListOf<String>()

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        startedSpanNames.add(span.name)
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {
        endedSpanNames.add(span.name)
    }

    override fun isEndRequired(): Boolean = true

    override fun forceFlush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
