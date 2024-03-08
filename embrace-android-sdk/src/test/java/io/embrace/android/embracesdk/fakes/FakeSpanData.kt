@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData

internal class FakeSpanData(
    private var name: String = "fake-span",
    private var kind: SpanKind = SpanKind.INTERNAL,
    private var spanContext: SpanContext = SpanContext.getInvalid(),
    private var parentSpanContext: SpanContext = SpanContext.getInvalid(),
    private var status: StatusData = StatusData.unset(),
    private var startEpochNanos: Long = 0L,
    private var attributes: Attributes = Attributes.builder().put("my-key", "my-value").build(),
    private var events: MutableList<EventData> = mutableListOf(
        EventData.create(
            0L,
            "fake-event",
            Attributes.builder().put("my-key", "my-value").build()
        )
    ),
    private var links: MutableList<LinkData> = mutableListOf(),
    private var endEpochNanos: Long = 0L,
    private var hasEnded: Boolean = true,
    private var resource: Resource = Resource.empty()
) : SpanData {
    override fun getName(): String = name
    override fun getKind(): SpanKind = kind
    override fun getSpanContext(): SpanContext = spanContext
    override fun getParentSpanContext(): SpanContext = parentSpanContext
    override fun getStatus(): StatusData = status
    override fun getStartEpochNanos(): Long = startEpochNanos
    override fun getAttributes(): Attributes = attributes
    override fun getEvents(): MutableList<EventData> = events
    override fun getLinks(): MutableList<LinkData> = links
    override fun getEndEpochNanos(): Long = endEpochNanos
    override fun hasEnded(): Boolean = hasEnded
    override fun getTotalRecordedEvents(): Int = events.size
    override fun getTotalRecordedLinks(): Int = links.size
    override fun getTotalAttributeCount(): Int = attributes.size()

    @Deprecated("Deprecated in Java")
    override fun getInstrumentationLibraryInfo() = InstrumentationLibraryInfo.empty()
    override fun getResource(): Resource = resource
}
