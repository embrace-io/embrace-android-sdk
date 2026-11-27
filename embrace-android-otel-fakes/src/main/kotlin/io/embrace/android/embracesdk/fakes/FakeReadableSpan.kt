package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

class FakeReadableSpan(
    private val snapshot: SpanData = FakeReadWriteSpan(),
) : ReadableSpan {

    var toSpanDataCount: Int = 0
        private set

    override fun toSpanData(): SpanData {
        toSpanDataCount++
        return snapshot
    }

    override val name: String get() = liveRead()
    override val status: StatusData get() = liveRead()
    override val parent: SpanContext get() = liveRead()
    override val spanContext: SpanContext get() = liveRead()
    override val spanKind: SpanKind get() = liveRead()
    override val startTimestamp: Long get() = liveRead()
    override val endTimestamp: Long? get() = liveRead()
    override val attributes: Map<String, Any> get() = liveRead()
    override val events: List<SpanEventData> get() = liveRead()
    override val droppedEventsCount: Int get() = liveRead()
    override val links: List<SpanLinkData> get() = liveRead()
    override val droppedLinksCount: Int get() = liveRead()
    override val resource: Resource get() = liveRead()
    override val instrumentationScopeInfo: InstrumentationScopeInfo get() = liveRead()
    override val isEnded: Boolean get() = liveRead()
    override val droppedAttributesCount: Int get() = liveRead()

    private fun liveRead(): Nothing = throw UnsupportedOperationException("read the live span instead of its snapshot")
}
