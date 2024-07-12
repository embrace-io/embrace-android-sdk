package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl
import io.embrace.android.embracesdk.opentelemetry.embSequenceId
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.ContextKey

internal val testSpan = EmbraceSpanData(
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

internal val testSpanSnapshot = Span(
    traceId = "snapshot-trace-id",
    spanId = "snapshot-span-id",
    parentSpanId = null,
    name = "snapshot",
    startTimeNanos = DEFAULT_FAKE_CURRENT_TIME,
    endTimeNanos = null,
    status = Span.Status.UNSET,
    events = emptyList(),
    attributes = emptyList()
)

internal val fakeContextKey = ContextKey.named<String>("fake-context-key")

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

internal val MAX_LENGTH_SPAN_NAME = "s".repeat(EmbraceSpanImpl.MAX_NAME_LENGTH)
internal val TOO_LONG_SPAN_NAME = "s".repeat(EmbraceSpanImpl.MAX_NAME_LENGTH + 1)
internal val MAX_LENGTH_EVENT_NAME = "s".repeat(EmbraceSpanEvent.MAX_EVENT_NAME_LENGTH)
internal val TOO_LONG_EVENT_NAME = "s".repeat(EmbraceSpanEvent.MAX_EVENT_NAME_LENGTH + 1)
internal val MAX_LENGTH_ATTRIBUTE_KEY = "s".repeat(EmbraceSpanImpl.MAX_ATTRIBUTE_KEY_LENGTH)
internal val TOO_LONG_ATTRIBUTE_KEY = "s".repeat(EmbraceSpanImpl.MAX_ATTRIBUTE_KEY_LENGTH + 1)
internal val MAX_LENGTH_ATTRIBUTE_VALUE = "s".repeat(EmbraceSpanImpl.MAX_ATTRIBUTE_VALUE_LENGTH)
internal val TOO_LONG_ATTRIBUTE_VALUE = "s".repeat(EmbraceSpanImpl.MAX_ATTRIBUTE_VALUE_LENGTH + 1)

internal val maxSizeAttributes = createMapOfSize(EmbraceSpanImpl.MAX_ATTRIBUTE_COUNT)
internal val tooBigAttributes = createMapOfSize(EmbraceSpanImpl.MAX_ATTRIBUTE_COUNT + 1)
internal val maxSizeEventAttributes = createMapOfSize(EmbraceSpanEvent.MAX_EVENT_ATTRIBUTE_COUNT)
internal val tooBigEventAttributes = createMapOfSize(EmbraceSpanEvent.MAX_EVENT_ATTRIBUTE_COUNT + 1)
internal val maxSizeEvents = createEventsListOfSize(EmbraceSpanImpl.MAX_EVENT_COUNT)
internal val tooBigEvents = createEventsListOfSize(EmbraceSpanImpl.MAX_EVENT_COUNT + 1)
