package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.spans.EmbraceSpanBuilder
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit

internal class EmbSpanBuilder(
    private val embraceSpanBuilder: EmbraceSpanBuilder,
    private val spanService: SpanService,
    private val clock: Clock
) : SpanBuilder {

    private val delegate = embraceSpanBuilder.otelSpanBuilder

    /**
     * TODO
     */
    override fun setParent(context: Context): SpanBuilder = this

    /**
     * TODO
     */
    override fun setNoParent(): SpanBuilder = this

    override fun addLink(spanContext: SpanContext): SpanBuilder = this.addLink(spanContext, Attributes.empty())

    /**
     * TODO
     */
    override fun addLink(spanContext: SpanContext, attributes: Attributes): SpanBuilder = this

    /**
     * TODO
     */
    override fun setAttribute(key: String, value: String): SpanBuilder = this

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): SpanBuilder = setAttribute(key.key, value.toString())

    override fun setAttribute(key: String, value: Long): SpanBuilder = setAttribute(key, value.toString())

    override fun setAttribute(key: String, value: Double): SpanBuilder = setAttribute(key, value.toString())

    override fun setAttribute(key: String, value: Boolean): SpanBuilder = setAttribute(key, value.toString())

    override fun setSpanKind(spanKind: SpanKind): SpanBuilder {
        delegate.setSpanKind(spanKind)
        return this
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): SpanBuilder {
        delegate.setStartTimestamp(startTimestamp, unit)
        return this
    }

    override fun startSpan(): Span {
        return spanService.createSpan(embraceSpanBuilder)?.let { embraceSpan ->
            return if (embraceSpan.start()) {
                EmbSpan(
                    embraceSpan = embraceSpan,
                    clock = clock,
                )
            } else {
                Span.getInvalid()
            }
        } ?: Span.getInvalid()
    }
}
