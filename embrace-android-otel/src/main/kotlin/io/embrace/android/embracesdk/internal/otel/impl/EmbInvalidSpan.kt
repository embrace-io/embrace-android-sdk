package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.k2j.tracing.SpanContextAdapter
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.Link
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanEvent
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
internal class EmbInvalidSpan : Span {

    override fun attributes(): Map<String, Any> = emptyMap()

    override var name: String = ""
    override val parent: SpanContext? = null
    override val spanContext: SpanContext = SpanContextAdapter(OtelJavaSpanContext.getInvalid())
    override val spanKind: SpanKind = SpanKind.INTERNAL
    override val startTimestamp: Long = 0
    override var status: StatusCode = StatusCode.Unset

    override fun addEvent(name: String, timestamp: Long?, action: AttributeContainer.() -> Unit) {
    }

    override fun addLink(spanContext: SpanContext, action: AttributeContainer.() -> Unit) {
    }

    override fun end() {
    }

    override fun end(timestamp: Long) {
    }

    override fun events(): List<SpanEvent> = emptyList()

    override fun isRecording(): Boolean = false

    override fun links(): List<Link> = emptyList()

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
