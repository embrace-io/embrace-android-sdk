package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.model.SpanEvent
import io.opentelemetry.kotlin.tracing.model.SpanLink

class EmbSpan(
    private val impl: EmbraceSdkSpan,
    private val clock: Clock,
    private val openTelemetry: OpenTelemetry,
) : Span, SpanCreationAction {

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
        get() = impl.spanContext ?: openTelemetry.spanContext.invalid

    override fun isRecording(): Boolean = impl.isRecording

    override fun addEvent(name: String, timestamp: Long?, attributes: (AttributesMutator.() -> Unit)?) {
        val container = EmbAttributesMutator()
        attributes?.invoke(container)
        impl.addEvent(name, timestamp, container.attributes.mapValues { it.value.toString() })
    }

    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) {
        val container = EmbAttributesMutator()
        attributes?.invoke(container)
        impl.addLink(spanContext, container.attributes.mapValues { it.value.toString() })
    }

    val attributes: Map<String, Any>
        get() = impl.attributes()

    val name: String
        get() = impl.name()

    override fun setName(name: String) {
        impl.updateName(name)
    }

    override val parent: SpanContext
        get() = impl.parent?.spanContext ?: openTelemetry.spanContext.invalid

    val spanKind: SpanKind
        get() = impl.spanKind

    val startTimestamp: Long
        get() = impl.getStartTimeMs() ?: 0

    val status: StatusData
        get() = impl.status

    override fun setStatus(status: StatusData) {
        if (isRecording()) {
            impl.status = status
        }
    }

    val events: List<SpanEvent>
        get() = impl.events().map {
            EmbSpanEvent(
                it.name ?: "",
                it.timestampNanos ?: 0,
                it.attributes.toEmbAttributesMutator()
            )
        }

    val links: List<SpanLink>
        get() = impl.links().map {
            EmbLink(it.retrieveSpanContext(), it.attributes.toEmbAttributesMutator())
        }

    private fun List<Attribute>?.toEmbAttributesMutator(): EmbAttributesMutator {
        val raw = this ?: emptyList()
        val attrs = raw.filter { entry -> entry.key == null || entry.data == null }
        val map = mutableMapOf<String, Any>()
        attrs.forEach { entry -> map[checkNotNull(entry.key)] = checkNotNull(entry.data) }
        return EmbAttributesMutator(map)
    }

    private fun io.embrace.android.embracesdk.internal.payload.Link.retrieveSpanContext(): SpanContext {
        return openTelemetry.spanContext.create(
            traceId = checkNotNull(traceId),
            spanId = checkNotNull(spanId),
            traceFlags = openTelemetry.traceFlags.default,
            traceState = openTelemetry.traceState.default,
            isRemote = false,
        )
    }
}
