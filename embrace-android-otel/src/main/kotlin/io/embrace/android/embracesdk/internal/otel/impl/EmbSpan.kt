package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData

class EmbSpan(
    private val impl: EmbraceSdkSpan,
    private val clock: Clock,
    private val openTelemetry: OpenTelemetry,
) : Span, SpanCreationAction {

    override fun setStringAttribute(key: String, value: String) {
        impl.addCustomAttribute(key, value)
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
        impl.addCustomAttribute(key, value)
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
        impl.addCustomAttribute(key, value)
    }

    override fun setLongAttribute(key: String, value: Long) {
        impl.addCustomAttribute(key, value)
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
        impl.addCustomAttribute(key, value)
    }

    override fun setDoubleAttribute(key: String, value: Double) {
        impl.addCustomAttribute(key, value)
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
        impl.addCustomAttribute(key, value)
    }

    override fun setBooleanAttribute(key: String, value: Boolean) {
        impl.addCustomAttribute(key, value)
    }

    override fun setByteArrayAttribute(key: String, value: ByteArray) {
        impl.addCustomAttribute(key, value)
    }

    override fun setAnyValueAttribute(key: String, value: AnyValue) {
        impl.addCustomAttribute(key, value)
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
        impl.addCustomEvent(name, timestamp, container.attributes)
    }

    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) {
        val container = EmbAttributesMutator()
        attributes?.invoke(container)
        impl.addCustomLink(spanContext, container.attributes)
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
        get() = impl.events()

    val links: List<Link>
        get() = impl.links()
}
