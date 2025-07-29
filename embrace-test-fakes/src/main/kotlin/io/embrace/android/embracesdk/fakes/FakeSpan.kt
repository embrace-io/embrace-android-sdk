package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.Link
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanEvent
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

@OptIn(ExperimentalApi::class)
class FakeSpan : Span {

    var attrs: MutableMap<String, String> = mutableMapOf()

    override fun attributes(): Map<String, Any> = attrs

    override var name: String = ""
    override val parent: SpanContext = FakeSpanContext()
    override val spanContext: SpanContext = FakeSpanContext()
    override val spanKind: SpanKind = SpanKind.INTERNAL
    override val startTimestamp: Long = -1
    override var status: StatusCode = StatusCode.Unset

    override fun addEvent(name: String, timestamp: Long?, attributes: AttributeContainer.() -> Unit) {
    }

    override fun addLink(spanContext: SpanContext, attributes: AttributeContainer.() -> Unit) {
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
        attrs[key] = value
    }

    override fun setStringListAttribute(key: String, value: List<String>) {
    }
}
