package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

internal class FakeSpanBuilder(
    val spanName: String
) : SpanBuilder {

    var spanKind: SpanKind? = null
    var parentContext: Context = Context.root()
    var startTimestampMs: Long? = null

    override fun setParent(context: Context): SpanBuilder {
        parentContext = context
        return this
    }

    override fun setNoParent(): SpanBuilder {
        parentContext = Context.root()
        return this
    }

    override fun addLink(spanContext: SpanContext): SpanBuilder {
        TODO("Not yet implemented")
    }

    override fun addLink(spanContext: SpanContext, attributes: Attributes): SpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: String): SpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: Long): SpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: Double): SpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: Boolean): SpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setSpanKind(spanKind: SpanKind): SpanBuilder {
        this.spanKind = spanKind
        return this
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): SpanBuilder {
        startTimestampMs = unit.toMillis(startTimestamp)
        return this
    }

    override fun startSpan(): FakeSpan = FakeSpan(this)

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): SpanBuilder {
        TODO("Not yet implemented")
    }
}
