package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanBuilderWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit

class EmbSpanBuilder(
    private val otelSpanBuilderWrapper: OtelSpanBuilderWrapper,
    private val spanService: SpanService,
    private val clock: Clock,
) : SpanBuilder {

    override fun setParent(context: Context): SpanBuilder {
        otelSpanBuilderWrapper.setParentContext(context)
        return this
    }

    override fun setNoParent(): SpanBuilder {
        otelSpanBuilderWrapper.setNoParent()
        return this
    }

    override fun addLink(spanContext: SpanContext): SpanBuilder = addLink(spanContext, Attributes.empty())

    override fun addLink(spanContext: SpanContext, attributes: Attributes): SpanBuilder = this

    override fun setAttribute(key: String, value: String): SpanBuilder {
        otelSpanBuilderWrapper.setCustomAttribute(key, value)
        return this
    }

    override fun setAttribute(key: String, value: Long): SpanBuilder = setAttribute(key, value.toString())

    override fun setAttribute(key: String, value: Double): SpanBuilder = setAttribute(key, value.toString())

    override fun setAttribute(key: String, value: Boolean): SpanBuilder = setAttribute(key, value.toString())

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): SpanBuilder =
        setAttribute(key.key, value.toString())

    override fun setSpanKind(spanKind: SpanKind): SpanBuilder {
        otelSpanBuilderWrapper.setSpanKind(spanKind)
        return this
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): SpanBuilder {
        otelSpanBuilderWrapper.startTimeMs = unit.toMillis(startTimestamp)
        return this
    }

    override fun startSpan(): Span {
        spanService.createSpan(otelSpanBuilderWrapper)?.let { embraceSpan ->
            if (embraceSpan.start()) {
                return EmbSpan(
                    embraceSpan = embraceSpan,
                    clock = clock,
                )
            }
        }

        return Span.getInvalid()
    }
}
