package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.tracing.data.StatusData
import io.opentelemetry.kotlin.tracing.model.Link
import io.opentelemetry.kotlin.tracing.model.Span
import io.opentelemetry.kotlin.tracing.model.SpanContext
import io.opentelemetry.kotlin.tracing.model.SpanEvent
import io.opentelemetry.kotlin.tracing.model.SpanKind

@OptIn(ExperimentalApi::class)
class EmbSpan(
    private val impl: EmbraceSdkSpan,
    private val clock: Clock,
    private val openTelemetry: OpenTelemetry,
) : Span {

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
        get() = impl.spanContext ?: openTelemetry.spanContextFactory.invalid

    override fun isRecording(): Boolean = impl.isRecording

    override fun addEvent(name: String, timestamp: Long?, attributes: (MutableAttributeContainer.() -> Unit)?) {
        val container = EmbMutableAttributeContainer()
        if (attributes != null) {
            container.attributes()
        }
        impl.addEvent(name, timestamp, container.attributes)
    }

    override fun addLink(spanContext: SpanContext, attributes: (MutableAttributeContainer.() -> Unit)?) {
        val container = EmbMutableAttributeContainer()
        if (attributes != null) {
            container.attributes()
        }
        impl.addLink(spanContext, container.attributes)
    }

    override val attributes: Map<String, Any>
        get() = impl.attributes()

    override var name: String
        get() = impl.name()
        set(value) {
            impl.updateName(value)
        }

    override val parent: SpanContext
        get() = impl.parent?.spanContext ?: openTelemetry.spanContextFactory.invalid

    override val spanKind: SpanKind
        get() = impl.spanKind

    override val startTimestamp: Long
        get() = impl.getStartTimeMs() ?: 0

    override var status: StatusData
        get() = impl.status
        set(value) {
            if (isRecording()) {
                impl.status = value
            }
        }

    override val events: List<SpanEvent>
        get() = impl.events().map {
            EmbSpanEvent(
                it.name ?: "",
                it.timestampNanos ?: 0,
                it.attributes.toMutableAttributeContainer()
            )
        }

    override val links: List<Link>
        get() = impl.links().map {
            EmbLink(it.retrieveSpanContext(), it.attributes.toMutableAttributeContainer())
        }

    private fun List<Attribute>?.toMutableAttributeContainer(): MutableAttributeContainer {
        val raw = this ?: emptyList()
        val attrs = raw.filter { entry -> entry.key == null || entry.data == null }
        val map = attrs.associate { entry ->
            Pair(checkNotNull(entry.key), checkNotNull(entry.data))
        }
        return EmbMutableAttributeContainer(map.toMutableMap())
    }

    private fun io.embrace.android.embracesdk.internal.payload.Link.retrieveSpanContext(): SpanContext {
        return openTelemetry.spanContextFactory.create(
            traceId = checkNotNull(traceId),
            spanId = checkNotNull(spanId),
            traceFlags = openTelemetry.traceFlagsFactory.default,
            traceState = openTelemetry.traceStateFactory.default,
        )
    }
}
