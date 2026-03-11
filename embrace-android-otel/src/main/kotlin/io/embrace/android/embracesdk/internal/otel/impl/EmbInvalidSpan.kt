package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.data.StatusData
import io.opentelemetry.kotlin.tracing.model.Span
import io.opentelemetry.kotlin.tracing.model.SpanContext
import io.opentelemetry.kotlin.tracing.model.SpanCreationAction
import io.opentelemetry.kotlin.tracing.model.SpanEvent
import io.opentelemetry.kotlin.tracing.model.SpanKind
import io.opentelemetry.kotlin.tracing.model.SpanLink

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
internal class EmbInvalidSpan(openTelemetry: OpenTelemetry) : Span, SpanCreationAction {

    override val attributes: Map<String, Any> = emptyMap()

    override var name: String = ""
    override val parent: SpanContext = openTelemetry.spanContext.invalid
    override val spanContext: SpanContext = openTelemetry.spanContext.invalid
    override val spanKind: SpanKind = SpanKind.INTERNAL
    override val startTimestamp: Long = 0
    override var status: StatusData = StatusData.Unset

    override fun addEvent(name: String, timestamp: Long?, attributes: (AttributesMutator.() -> Unit)?) {
    }

    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) {
    }

    override fun end() {
    }

    override fun end(timestamp: Long) {
    }

    override val events: List<SpanEvent> = emptyList()

    override fun isRecording(): Boolean = false

    override val links: List<SpanLink> = emptyList()

    override fun setBooleanAttribute(key: String, value: Boolean) {
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) {
    }

    override fun setDoubleAttribute(key: String, value: Double) {
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) {
    }

    override fun setLongAttribute(key: String, value: Long) {
    }

    override fun setLongListAttribute(key: String, value: List<Long>) {
    }

    override fun setStringAttribute(key: String, value: String) {
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
    }
}
