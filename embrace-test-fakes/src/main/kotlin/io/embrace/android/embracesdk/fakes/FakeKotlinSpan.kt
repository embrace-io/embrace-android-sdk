package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.StatusCode
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.Link
import io.embrace.opentelemetry.kotlin.tracing.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.SpanEvent
import io.embrace.opentelemetry.kotlin.tracing.SpanKind

@ExperimentalApi
class FakeKotlinSpan(
    override var name: String,
    override var parent: SpanContext?,
    override val spanKind: SpanKind,
    override val startTimestamp: Long,
) : io.embrace.opentelemetry.kotlin.tracing.Span {

    private var inProgress = true

    override fun attributes(): Map<String, Any> = emptyMap()

    // FIXME: temp, requires access to SpanContextAdapter in opentelemetry-kotlin
    override val spanContext: SpanContext = throw UnsupportedOperationException()

    override var status: StatusCode = StatusCode.Unset

    override fun addEvent(name: String, timestamp: Long?, action: AttributeContainer.() -> Unit) {
    }

    override fun addLink(spanContext: SpanContext, action: AttributeContainer.() -> Unit) {
    }

    override fun end() {
        inProgress = false
    }

    override fun end(timestamp: Long) {
        inProgress = false
    }

    override fun events(): List<SpanEvent> = emptyList()

    override fun isRecording(): Boolean = inProgress

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
