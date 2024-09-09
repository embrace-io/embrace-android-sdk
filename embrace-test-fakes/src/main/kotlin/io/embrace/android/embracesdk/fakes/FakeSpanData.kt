@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.KeySpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.fromMap
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlin.random.Random

class FakeSpanData(
    private var name: String = "fake-started-span",
    private var kind: SpanKind = SpanKind.INTERNAL,
    private var spanContext: SpanContext = newTraceRootContext(),
    private var parentSpanContext: SpanContext = SpanContext.getInvalid(),
    private var startEpochNanos: Long = DEFAULT_START_TIME_MS.millisToNanos(),
    private var attributes: Attributes =
        Attributes.builder().fromMap(
            mapOf(
                EmbType.Performance.Default.toEmbraceKeyValuePair(),
                KeySpan.toEmbraceKeyValuePair(),
                Pair("my-key", "my-value")
            )
        ).build(),
    private var events: MutableList<EventData> = mutableListOf(
        EventData.create(
            startEpochNanos + 1000000L,
            "fake-event",
            Attributes.builder().put("my-key", "my-value").build()
        )
    ),
    private var links: MutableList<LinkData> = mutableListOf(),
    private var resource: Resource = Resource.empty(),
    var spanStatus: StatusData = StatusData.unset(),
    var endTimeNanos: Long = 0L
) : SpanData {
    override fun getName(): String = name
    override fun getKind(): SpanKind = kind
    override fun getSpanContext(): SpanContext = spanContext
    override fun getParentSpanContext(): SpanContext = parentSpanContext
    override fun getStatus(): StatusData = spanStatus
    override fun getStartEpochNanos(): Long = startEpochNanos
    override fun getAttributes(): Attributes = attributes
    override fun getEvents(): MutableList<EventData> = events
    override fun getLinks(): MutableList<LinkData> = links
    override fun getEndEpochNanos(): Long = endTimeNanos
    override fun hasEnded(): Boolean = status != StatusData.unset()
    override fun getTotalRecordedEvents(): Int = events.size
    override fun getTotalRecordedLinks(): Int = links.size
    override fun getTotalAttributeCount(): Int = attributes.size()

    @Deprecated("Deprecated in Java")
    override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = InstrumentationLibraryInfo.empty()
    override fun getResource(): Resource = resource

    companion object {
        private const val DEFAULT_START_TIME_MS = FakeClock.DEFAULT_FAKE_CURRENT_TIME
        val perfSpanSnapshot: FakeSpanData = FakeSpanData(name = "snapshot-perf-span")
        val perfSpanCompleted: FakeSpanData =
            FakeSpanData(
                name = "completed-perf-span",
                spanStatus = StatusData.ok(),
                endTimeNanos = (DEFAULT_START_TIME_MS + 60000L).millisToNanos()
            )

        private fun newTraceRootContext() = SpanContext.create(
            TraceId.fromLongs(Random.nextLong(), Random.nextLong()).toString(),
            SpanId.fromLong(Random.nextLong()).toString(),
            TraceFlags.getDefault(),
            TraceState.getDefault()
        )
    }
}
