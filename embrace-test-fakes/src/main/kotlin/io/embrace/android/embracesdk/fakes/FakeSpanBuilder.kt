package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import io.embrace.opentelemetry.kotlin.context.Context
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
class FakeSpanBuilder : OtelJavaSpanBuilder {

    override fun startSpan(): FakeOtelJavaSpan = FakeOtelJavaSpan(parentContext = parentContext)

    var parentContext: Context = fakeOpenTelemetry().contextFactory.root()
    var attributes: MutableMap<Any, Any> = mutableMapOf()

    override fun setParent(context: OtelJavaContext): OtelJavaSpanBuilder = this

    override fun setNoParent(): OtelJavaSpanBuilder = this

    override fun addLink(spanContext: OtelJavaSpanContext): OtelJavaSpanBuilder = this

    override fun addLink(spanContext: OtelJavaSpanContext, attributes: OtelJavaAttributes): OtelJavaSpanBuilder = this

    override fun setAttribute(key: String, value: String): OtelJavaSpanBuilder = this

    override fun setAttribute(key: String, value: Long): OtelJavaSpanBuilder = this

    override fun setAttribute(key: String, value: Double): OtelJavaSpanBuilder = this

    override fun setAttribute(key: String, value: Boolean): OtelJavaSpanBuilder = this

    override fun setSpanKind(spanKind: OtelJavaSpanKind): OtelJavaSpanBuilder = this

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): OtelJavaSpanBuilder = this

    override fun <T : Any> setAttribute(key: OtelJavaAttributeKey<T>, value: T): OtelJavaSpanBuilder = this
}
