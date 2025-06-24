package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanCreator
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import java.util.concurrent.TimeUnit

class EmbOtelJavaSpanBuilder(
    private val otelSpanCreator: OtelSpanCreator,
    private val spanService: SpanService,
    private val clock: OtelJavaClock,
) : OtelJavaSpanBuilder {

    private val otelSpanStartArgs = otelSpanCreator.spanStartArgs

    override fun setParent(context: OtelJavaContext): OtelJavaSpanBuilder {
        otelSpanStartArgs.parentContext = context
        return this
    }

    override fun setNoParent(): OtelJavaSpanBuilder {
        otelSpanStartArgs.parentContext = OtelJavaContext.root()
        return this
    }

    override fun addLink(spanContext: OtelJavaSpanContext): OtelJavaSpanBuilder = addLink(spanContext, OtelJavaAttributes.empty())

    override fun addLink(spanContext: OtelJavaSpanContext, attributes: OtelJavaAttributes): OtelJavaSpanBuilder = this

    override fun setAttribute(key: String, value: String): OtelJavaSpanBuilder {
        otelSpanStartArgs.customAttributes[key] = value
        return this
    }

    override fun setAttribute(key: String, value: Long): OtelJavaSpanBuilder = setAttribute(key, value.toString())

    override fun setAttribute(key: String, value: Double): OtelJavaSpanBuilder = setAttribute(key, value.toString())

    override fun setAttribute(key: String, value: Boolean): OtelJavaSpanBuilder = setAttribute(key, value.toString())

    override fun <T : Any> setAttribute(key: OtelJavaAttributeKey<T>, value: T): OtelJavaSpanBuilder =
        setAttribute(key.key, value.toString())

    override fun setSpanKind(spanKind: OtelJavaSpanKind): OtelJavaSpanBuilder {
        otelSpanStartArgs.spanKind = spanKind
        return this
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): OtelJavaSpanBuilder {
        otelSpanStartArgs.startTimeMs = unit.toMillis(startTimestamp)
        return this
    }

    override fun startSpan(): OtelJavaSpan {
        spanService.createSpan(otelSpanCreator)?.let { embraceSpan ->
            if (embraceSpan.start()) {
                return EmbOtelJavaSpan(
                    embraceSpan = embraceSpan,
                    clock = clock,
                )
            }
        }

        return OtelJavaSpan.getInvalid()
    }
}
