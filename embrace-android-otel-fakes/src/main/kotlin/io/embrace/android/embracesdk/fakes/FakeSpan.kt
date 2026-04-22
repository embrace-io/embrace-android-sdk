package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.model.SpanLink
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.model.SpanEvent
import io.opentelemetry.kotlin.tracing.SpanKind

class FakeSpan(
    name: String = "",
    override val parent: SpanContext = FakeSpanContext(),
    override val spanContext: SpanContext = FakeSpanContext(),
    val spanKind: SpanKind = SpanKind.INTERNAL,
    val startTimestamp: Long = -1,
    status: StatusData = StatusData.Unset,
    val links: List<SpanLink> = emptyList(),
    val events: List<SpanEvent> = emptyList(),
    var recording: Boolean = true,
) : Span {

    private var nameImpl: String = name
    private var statusImpl: StatusData = status

    val name: String get() = nameImpl
    val status: StatusData get() = statusImpl

    var attrs: MutableMap<String, String> = mutableMapOf()

    val attributes: Map<String, Any> = attrs

    override fun setName(name: String) { nameImpl = name }

    override fun setStatus(status: StatusData) { statusImpl = status }

    override fun addEvent(name: String, timestamp: Long?, attributes: (AttributesMutator.() -> Unit)?) {
    }

    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) {
    }

    override fun end() {
        recording = false
    }

    override fun end(timestamp: Long) {
        recording = false
    }

    override fun isRecording(): Boolean = recording

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
