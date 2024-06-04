package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.IdGenerator
import java.util.concurrent.TimeUnit

internal class FakeSpan(
    val fakeSpanBuilder: FakeSpanBuilder
) : Span {

    private val spanContext: SpanContext =
        SpanContext.create(
            if (fakeSpanBuilder.parentContext == Context.root()) {
                IdGenerator.random().generateTraceId()
            } else {
                Span.fromContext(fakeSpanBuilder.parentContext).spanContext.traceId
            },
            IdGenerator.random().generateSpanId(),
            TraceFlags.getDefault(),
            TraceState.getDefault()
        )

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): Span {
        TODO("Not yet implemented")
    }

    override fun addEvent(name: String, attributes: Attributes): Span {
        TODO("Not yet implemented")
    }

    override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span {
        TODO("Not yet implemented")
    }

    override fun setStatus(statusCode: StatusCode, description: String): Span {
        TODO("Not yet implemented")
    }

    override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span {
        TODO("Not yet implemented")
    }

    override fun updateName(name: String): Span {
        TODO("Not yet implemented")
    }

    override fun end() {
        TODO("Not yet implemented")
    }

    override fun end(timestamp: Long, unit: TimeUnit) {
        TODO("Not yet implemented")
    }

    override fun getSpanContext(): SpanContext = spanContext

    override fun isRecording(): Boolean {
        TODO("Not yet implemented")
    }
}
