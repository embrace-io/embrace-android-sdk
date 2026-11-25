package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan

class FakeReadWriteSpan(
    private val impl: FakeSpan = FakeSpan(),
) : Span by impl, ReadWriteSpan {

    override var spanContext: SpanContext = impl.spanContext
    override val attributes: Map<String, Any> = impl.attrs
    override val events: List<SpanEventData> = emptyList()
    override val instrumentationScopeInfo: InstrumentationScopeInfo
        get() = throw UnsupportedOperationException()
    override val links: List<SpanLinkData> = emptyList()
    override val droppedEventsCount: Int = 0
    override val droppedLinksCount: Int = 0
    override val droppedAttributesCount: Int = 0
    override val resource: Resource
        get() = throw UnsupportedOperationException()

    override val isEnded: Boolean = true
    override val endTimestamp: Long? = null
    override fun toSpanData(): SpanData = this

    override val name: String get() = impl.name
    override val status: StatusData get() = impl.status
    override val spanKind: SpanKind get() = impl.spanKind
    override val startTimestamp: Long get() = impl.startTimestamp
}
