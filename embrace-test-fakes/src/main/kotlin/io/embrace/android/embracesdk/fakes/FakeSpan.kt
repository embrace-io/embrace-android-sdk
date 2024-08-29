package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.spans.getEmbraceSpan
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.trace.IdGenerator
import java.util.concurrent.TimeUnit

public class FakeSpan(
    public val fakeSpanBuilder: FakeSpanBuilder
) : Span {

    private val spanContext: SpanContext =
        SpanContext.create(
            fakeSpanBuilder.parentContext.getEmbraceSpan()?.traceId ?: IdGenerator.random().generateTraceId(),
            IdGenerator.random().generateSpanId(),
            TraceFlags.getDefault(),
            TraceState.getDefault()
        )

    private var isRecording = true
    private var status: StatusCode = StatusCode.UNSET
    private var statusDescription: String = ""

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): Span {
        fakeSpanBuilder.setAttribute(key, value)
        return this
    }

    override fun addEvent(name: String, attributes: Attributes): Span {
        TODO("Not yet implemented")
    }

    override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span {
        TODO("Not yet implemented")
    }

    override fun setStatus(statusCode: StatusCode, description: String): Span {
        status = statusCode
        statusDescription = description
        return this
    }

    override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span {
        TODO("Not yet implemented")
    }

    override fun updateName(name: String): Span {
        TODO("Not yet implemented")
    }

    override fun end() {
        isRecording = false
    }

    override fun end(timestamp: Long, unit: TimeUnit): Unit = end()

    override fun getSpanContext(): SpanContext = spanContext

    override fun isRecording(): Boolean = isRecording
}
