package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.opentelemetry.embSequenceId
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.ContextKey

val testSpan: Span = EmbraceSpanData(
    traceId = "19bb482ec1c7e6b2f10fb89e0ccc85fa",
    spanId = "342eb9c7f8cb54ff",
    parentSpanId = SpanId.getInvalid(),
    name = "emb-sdk-init",
    startTimeNanos = 1681972471806000000L,
    endTimeNanos = 1681972471871000000L,
    status = StatusCode.UNSET,
    events = listOf(
        checkNotNull(
            EmbraceSpanEvent.create(
                name = "start-time",
                timestampMs = 1681972471807L,
                attributes = mapOf(Pair("test1", "value1"), Pair("test2", "value2"))
            )
        ),
        checkNotNull(
            EmbraceSpanEvent.create(
                name = "end-span-event",
                timestampMs = 1681972471871L,
                attributes = null
            )
        )
    ),
    attributes = mapOf(
        Pair(embSequenceId.name, "3"),
        EmbType.Performance.Default.toEmbraceKeyValuePair(),
    )
).toNewPayload()

val fakeContextKey: ContextKey<String> = ContextKey.named<String>("fake-context-key")

private fun createMapOfSize(size: Int): Map<String, String> {
    val mutableMap = mutableMapOf<String, String>()
    repeat(size) {
        mutableMap[it.toString()] = "value"
    }
    return mutableMap
}

private fun createEventsListOfSize(size: Int): List<EmbraceSpanEvent> {
    val events = mutableListOf<EmbraceSpanEvent>()
    repeat(size) {
        events.add(checkNotNull(EmbraceSpanEvent.create("name $it", 1L, emptyMap())))
    }
    return events
}

private const val MAX_EVENT_NAME_LENGTH = 100
private const val MAX_EVENT_ATTRIBUTE_COUNT = 10

private val limits = InstrumentedConfigImpl.otelLimits

val MAX_LENGTH_SPAN_NAME: String = "s".repeat(limits.getMaxNameLength())
val TOO_LONG_SPAN_NAME: String = "s".repeat(limits.getMaxNameLength() + 1)
val MAX_LENGTH_EVENT_NAME: String = "s".repeat(MAX_EVENT_NAME_LENGTH)
val TOO_LONG_EVENT_NAME: String = "s".repeat(MAX_EVENT_NAME_LENGTH + 1)
val MAX_LENGTH_ATTRIBUTE_KEY: String = "s".repeat(limits.getMaxAttributeKeyLength())
val TOO_LONG_ATTRIBUTE_KEY: String = "s".repeat(limits.getMaxAttributeKeyLength() + 1)
val MAX_LENGTH_ATTRIBUTE_VALUE: String = "s".repeat(limits.getMaxAttributeValueLength())
val TOO_LONG_ATTRIBUTE_VALUE: String = "s".repeat(limits.getMaxAttributeValueLength() + 1)

val maxSizeAttributes: Map<String, String> = createMapOfSize(limits.getMaxAttributeCount())
val tooBigAttributes: Map<String, String> = createMapOfSize(limits.getMaxAttributeCount() + 1)
val maxSizeEventAttributes: Map<String, String> = createMapOfSize(MAX_EVENT_ATTRIBUTE_COUNT)
val tooBigEventAttributes: Map<String, String> = createMapOfSize(MAX_EVENT_ATTRIBUTE_COUNT + 1)
val maxSizeEvents: List<EmbraceSpanEvent> = createEventsListOfSize(limits.getMaxEventCount())
val tooBigEvents: List<EmbraceSpanEvent> = createEventsListOfSize(limits.getMaxEventCount() + 1)
