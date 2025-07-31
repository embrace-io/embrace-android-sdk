package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.creator.ObjectCreator
import io.embrace.opentelemetry.kotlin.k2j.tracing.toOtelJava
import io.embrace.opentelemetry.kotlin.k2j.tracing.toOtelKotlin
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.Link
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanEvent
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.ImplicitContextKeyed
import io.opentelemetry.context.Scope

@OptIn(ExperimentalApi::class)
class EmbSpan(
    private val impl: EmbraceSdkSpan,
    private val clock: Clock,
    private val objectCreator: ObjectCreator,
) : Span, ImplicitContextKeyed {

    override fun setStringAttribute(key: String, value: String) {
        impl.addAttribute(key, value)
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        setStringAttribute(key, value.toString())
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        setStringAttribute(key, value.toString())
    }

    override fun setLongAttribute(key: String, value: Long) {
        setStringAttribute(key, value.toString())
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        setStringAttribute(key, value.toString())
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        setStringAttribute(key, value.toString())
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        setStringAttribute(key, value.toString())
    }

    override fun setBooleanAttribute(key: String, value: Boolean) {
        setStringAttribute(key, value.toString())
    }

    override fun end(): Unit = end(timestamp = clock.now())

    override fun end(timestamp: Long) {
        if (isRecording()) {
            impl.stop(endTimeMs = timestamp.nanosToMillis())
        }
    }

    override val spanContext: SpanContext
        get() = impl.spanContext?.toOtelKotlin() ?: objectCreator.spanContext.invalid

    override fun isRecording(): Boolean = impl.isRecording

    override fun addEvent(name: String, timestamp: Long?, attributes: AttributeContainer.() -> Unit) {
        val attrs = EmbAttributeContainer().apply(attributes).attributes()
        impl.addEvent(name, timestamp, attrs)
    }

    override fun addLink(spanContext: SpanContext, attributes: AttributeContainer.() -> Unit) {
        val attrs = EmbAttributeContainer().apply(attributes).attributes()
        impl.addLink(spanContext.toOtelJava(), attrs)
    }

    override fun attributes(): Map<String, Any> {
        return impl.attributes()
    }

    override var name: String
        get() = impl.name()
        set(value) {
            impl.updateName(value)
        }

    override val parent: SpanContext
        get() = impl.parent?.spanContext?.toOtelKotlin() ?: objectCreator.spanContext.invalid

    override val spanKind: SpanKind
        get() = impl.spanKind

    override val startTimestamp: Long
        get() = impl.getStartTimeMs() ?: 0

    override var status: StatusCode
        get() = impl.status
        set(value) {
            if (isRecording()) {
                impl.status = value
            }
        }

    override fun events(): List<SpanEvent> = impl.events().map {
        EmbSpanEvent(
            it.name ?: "",
            it.timestampNanos ?: 0,
            it.attributes.toAttributeContainer()
        )
    }

    override fun links(): List<Link> = impl.links().map {
        EmbLink(it.retrieveSpanContext(), it.attributes.toAttributeContainer())
    }

    override fun storeInContext(context: Context): Context {
        return impl.storeInContext(context)
    }

    override fun makeCurrent(): Scope {
        return impl.makeCurrent()
    }

    private fun List<Attribute>?.toAttributeContainer(): AttributeContainer {
        val raw = this ?: emptyList()
        val attrs = raw.filter { entry -> entry.key == null || entry.data == null }
        val map = attrs.associate { entry ->
            Pair(checkNotNull(entry.key), checkNotNull(entry.data))
        }
        return EmbAttributeContainer(map.toMutableMap())
    }

    private fun io.embrace.android.embracesdk.internal.payload.Link.retrieveSpanContext(): SpanContext {
        return objectCreator.spanContext.create(
            traceId = checkNotNull(traceId),
            spanId = checkNotNull(spanId),
            traceFlags = objectCreator.traceFlags.default,
            traceState = objectCreator.traceState.default,
        )
    }
}
