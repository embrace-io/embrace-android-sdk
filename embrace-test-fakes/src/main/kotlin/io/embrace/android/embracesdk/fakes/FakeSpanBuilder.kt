package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import java.util.concurrent.TimeUnit

class FakeSpanBuilder(
    val spanName: String,
    val tracerKey: TracerKey = TracerKey("fake-scope"),
) : OtelJavaSpanBuilder {

    var spanKind: OtelJavaSpanKind? = null
    var parentContext: OtelJavaContext = OtelJavaContext.root()
    var startTimestampMs: Long? = null
    var attributes: MutableMap<Any, Any> = mutableMapOf()

    override fun setParent(context: OtelJavaContext): OtelJavaSpanBuilder {
        parentContext = context
        return this
    }

    override fun setNoParent(): OtelJavaSpanBuilder {
        parentContext = OtelJavaContext.root()
        return this
    }

    override fun addLink(spanContext: OtelJavaSpanContext): OtelJavaSpanBuilder {
        TODO("Not yet implemented")
    }

    override fun addLink(spanContext: OtelJavaSpanContext, attributes: OtelJavaAttributes): OtelJavaSpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: String): OtelJavaSpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: Long): OtelJavaSpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: Double): OtelJavaSpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setAttribute(key: String, value: Boolean): OtelJavaSpanBuilder {
        TODO("Not yet implemented")
    }

    override fun setSpanKind(spanKind: OtelJavaSpanKind): OtelJavaSpanBuilder {
        this.spanKind = spanKind
        return this
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): OtelJavaSpanBuilder {
        startTimestampMs = unit.toMillis(startTimestamp)
        return this
    }

    override fun startSpan(): FakeSpan = FakeSpan(this)

    override fun <T : Any> setAttribute(key: OtelJavaAttributeKey<T>, value: T): OtelJavaSpanBuilder {
        attributes[key] = value
        return this
    }
}
