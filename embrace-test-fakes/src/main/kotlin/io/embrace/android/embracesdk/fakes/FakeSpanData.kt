@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.toOtelJava
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaEventData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaInstrumentationLibraryInfo
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLinkData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaResource
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanId
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceFlags
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceId
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceState
import kotlin.random.Random

class FakeSpanData(
    private var name: String = "fake-started-span",
    private var kind: OtelJavaSpanKind = OtelJavaSpanKind.INTERNAL,
    private var type: EmbType = EmbType.Performance.Default,
    private var spanContext: OtelJavaSpanContext = newTraceRootContext(),
    private var parentSpanContext: OtelJavaSpanContext = OtelJavaSpanContext.getInvalid(),
    private var startEpochNanos: Long = DEFAULT_START_TIME_MS.millisToNanos(),
    private var attributes: OtelJavaAttributes = DataValidator().truncateAttributes(
        mapOf(
            type.asPair(),
            Pair("my-key", "my-value")
        ),
        true
    ).toOtelJava(),
    private var events: MutableList<OtelJavaEventData> = mutableListOf(
        OtelJavaEventData.create(
            startEpochNanos + 1000000L,
            "fake-event",
            OtelJavaAttributes.builder().put("my-key", "my-value").build()
        )
    ),
    private var links: MutableList<OtelJavaLinkData> = mutableListOf(),
    private var resource: OtelJavaResource = OtelJavaResource.empty(),
    var spanStatus: OtelJavaStatusData = OtelJavaStatusData.unset(),
    var endTimeNanos: Long = 0L,
) : OtelJavaSpanData {
    override fun getName(): String = name
    override fun getKind(): OtelJavaSpanKind = kind
    override fun getSpanContext(): OtelJavaSpanContext = spanContext
    override fun getParentSpanContext(): OtelJavaSpanContext = parentSpanContext
    override fun getStatus(): OtelJavaStatusData = spanStatus
    override fun getStartEpochNanos(): Long = startEpochNanos
    override fun getAttributes(): OtelJavaAttributes = attributes
    override fun getEvents(): MutableList<OtelJavaEventData> = events
    override fun getLinks(): MutableList<OtelJavaLinkData> = links
    override fun getEndEpochNanos(): Long = endTimeNanos
    override fun hasEnded(): Boolean = endTimeNanos > 0
    override fun getTotalRecordedEvents(): Int = events.size
    override fun getTotalRecordedLinks(): Int = links.size
    override fun getTotalAttributeCount(): Int = attributes.size()

    @Deprecated("Deprecated in Java")
    @Suppress("TYPEALIAS_EXPANSION_DEPRECATION", "DEPRECATION")
    override fun getInstrumentationLibraryInfo(): OtelJavaInstrumentationLibraryInfo = OtelJavaInstrumentationLibraryInfo.empty()
    override fun getResource(): OtelJavaResource = resource

    companion object {
        private const val DEFAULT_START_TIME_MS = FakeClock.DEFAULT_FAKE_CURRENT_TIME
        val perfSpanSnapshot: FakeSpanData = FakeSpanData(name = "snapshot-perf-span")
        val perfSpanCompleted: FakeSpanData =
            FakeSpanData(
                name = "completed-perf-span",
                endTimeNanos = (DEFAULT_START_TIME_MS + 60000L).millisToNanos()
            )

        private fun newTraceRootContext() = OtelJavaSpanContext.create(
            OtelJavaTraceId.fromLongs(Random.nextLong(), Random.nextLong()).toString(),
            OtelJavaSpanId.fromLong(Random.nextLong()).toString(),
            OtelJavaTraceFlags.getDefault(),
            OtelJavaTraceState.getDefault()
        )
    }
}
