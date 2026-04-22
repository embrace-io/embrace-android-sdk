package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.StatusData

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
internal class EmbInvalidSpan(openTelemetry: OpenTelemetry) : Span, SpanCreationAction {

    override val parent: SpanContext = openTelemetry.spanContext.invalid
    override val spanContext: SpanContext = openTelemetry.spanContext.invalid

    override fun setName(name: String) {}

    override fun setStatus(status: StatusData) {}

    override fun addEvent(name: String, timestamp: Long?, attributes: (AttributesMutator.() -> Unit)?) {
    }

    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) {
    }

    override fun end() {
    }

    override fun end(timestamp: Long) {
    }

    override fun isRecording(): Boolean = false

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
