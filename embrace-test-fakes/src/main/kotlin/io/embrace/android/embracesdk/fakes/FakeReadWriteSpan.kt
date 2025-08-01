package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.InstrumentationScopeInfo
import io.embrace.opentelemetry.kotlin.resource.Resource
import io.embrace.opentelemetry.kotlin.tracing.data.EventData
import io.embrace.opentelemetry.kotlin.tracing.data.LinkData
import io.embrace.opentelemetry.kotlin.tracing.data.SpanData
import io.embrace.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.embrace.opentelemetry.kotlin.tracing.model.Span

@OptIn(ExperimentalApi::class)
class FakeReadWriteSpan(
    private val impl: FakeSpan = FakeSpan(),
) : Span by impl, ReadWriteSpan {

    override val attributes: Map<String, Any> = impl.attrs
    override val events: List<EventData> = emptyList()
    override val instrumentationScopeInfo: InstrumentationScopeInfo
        get() = throw UnsupportedOperationException()
    override val links: List<LinkData> = emptyList()
    override val resource: Resource
        get() = throw UnsupportedOperationException()

    override val hasEnded: Boolean = true
    override val endTimestamp: Long? = null
    override fun toSpanData(): SpanData = throw UnsupportedOperationException()
}
