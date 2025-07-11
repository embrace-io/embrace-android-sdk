package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.opentelemetry.kotlin.Clock
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

@OptIn(ExperimentalApi::class)
class EmbSpan(
    private val impl: EmbraceSdkSpan,
    private val clock: Clock,
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
            impl.stop(endTimeMs = timestamp)
        }
    }

    override val spanContext: SpanContext
        get() = SpanContextAdapter(impl.spanContext ?: OtelJavaSpanContext.getInvalid())

    override fun isRecording(): Boolean = impl.isRecording

    override fun addEvent(name: String, timestamp: Long?, action: AttributeContainer.() -> Unit) {
        val attrs = EmbAttributeContainer().apply(action).attributes()
        impl.addEvent(name, timestamp, attrs)
    }

    override fun addLink(spanContext: SpanContext, action: AttributeContainer.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun attributes(): Map<String, Any> {
        val attrs = impl.snapshot()?.attributes?.filter { it.key == null || it.data == null }
        return attrs?.associate { Pair(checkNotNull(it.key), checkNotNull(it.data)) } ?: emptyMap()
    }

    override var name: String
        get() = impl.snapshot()?.name ?: ""
        set(value) {
            impl.updateName(value)
        }

    override val parent: SpanContext?
        get() = impl.parent?.spanContext?.let(::SpanContextAdapter)

    override val spanKind: SpanKind
        get() = TODO("Not yet implemented")

    override val startTimestamp: Long
        get() = impl.getStartTimeMs() ?: 0

    override var status: StatusCode
        get() = TODO("Not yet implemented")
        set(value) {
            impl.setStatus(value)
        }

    override fun events(): List<SpanEvent> {
        TODO("Not yet implemented")
    }

    override fun links(): List<Link> {
        TODO("Not yet implemented")
    }
}
