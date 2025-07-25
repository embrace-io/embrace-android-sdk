package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.attrs.embSequenceId
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.PropertyUtils
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContextKey
import io.embrace.opentelemetry.kotlin.tracing.StatusCode

@OptIn(ExperimentalApi::class)
val testSpan: Span = EmbraceSpanData(
    traceId = "19bb482ec1c7e6b2f10fb89e0ccc85fa",
    spanId = "342eb9c7f8cb54ff",
    parentSpanId = OtelIds.invalidSpanId,
    name = "emb-sdk-init",
    startTimeNanos = 1681972471806000000L,
    endTimeNanos = 1681972471871000000L,
    status = StatusCode.Unset,
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
        EmbType.Performance.Default.asPair(),
    )
).toEmbracePayload()

val testSpanSnapshot: Span = Span(
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

val fakeContextKey: OtelJavaContextKey<String> = OtelJavaContextKey.named<String>("fake-context-key")

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

private val limits = InstrumentedConfigImpl.otelLimits

val MAX_LENGTH_SPAN_NAME: String = "s".repeat(limits.getMaxNameLength())
val TOO_LONG_SPAN_NAME: String = "s".repeat(limits.getMaxNameLength() + 1)
val TRUNCATED_TOO_LONG_SPAN_NAME: String = PropertyUtils.truncate(TOO_LONG_SPAN_NAME, limits.getMaxNameLength())

// Max length for internal spans name need to take into account the "emb-" prefix that will be added before truncation is applied
val MAX_LENGTH_INTERNAL_SPAN_NAME: String = "s".repeat(limits.getMaxInternalNameLength() - 4)
val TOO_LONG_INTERNAL_SPAN_NAME: String = "s".repeat(limits.getMaxInternalNameLength() - 3)
val MAX_LENGTH_EVENT_NAME: String = "s".repeat(limits.getMaxNameLength())
val TOO_LONG_EVENT_NAME: String = "s".repeat(limits.getMaxNameLength() + 1)
val TRUNCATED_TOO_LONG_EVENT_NAME: String = PropertyUtils.truncate(TOO_LONG_EVENT_NAME, limits.getMaxNameLength())
val MAX_LENGTH_ATTRIBUTE_KEY: String = "s".repeat(limits.getMaxCustomAttributeKeyLength())
val TOO_LONG_ATTRIBUTE_KEY: String = "s".repeat(limits.getMaxCustomAttributeKeyLength() + 1)
val TRUNCATED_TOO_LONG_ATTRIBUTE_KEY: String = PropertyUtils.truncate(TOO_LONG_ATTRIBUTE_KEY, limits.getMaxCustomAttributeKeyLength())
val MAX_LENGTH_ATTRIBUTE_VALUE: String = "s".repeat(limits.getMaxCustomAttributeValueLength())
val TOO_LONG_ATTRIBUTE_VALUE: String = "s".repeat(limits.getMaxCustomAttributeValueLength() + 1)
val TRUNCATED_TOO_LONG_ATTRIBUTE_VALUE: String = PropertyUtils.truncate(TOO_LONG_ATTRIBUTE_VALUE, limits.getMaxCustomAttributeValueLength())
val MAX_LENGTH_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN: String = "s".repeat(limits.getMaxInternalAttributeKeyLength())
val TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN: String = "s".repeat(limits.getMaxInternalAttributeKeyLength() + 1)
val TRUNCATED_TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN: String =
    PropertyUtils.truncate(TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN, limits.getMaxInternalAttributeKeyLength())
val MAX_LENGTH_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN: String =
    "s".repeat(limits.getMaxInternalAttributeValueLength())
val TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN: String =
    "s".repeat(limits.getMaxInternalAttributeValueLength() + 1)
val TRUNCATED_TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN: String =
    PropertyUtils.truncate(TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN, limits.getMaxInternalAttributeValueLength())

val maxSizeCustomAttributes: Map<String, String> = createMapOfSize(limits.getMaxCustomAttributeCount())
val tooBigCustomAttributes: Map<String, String> = createMapOfSize(limits.getMaxCustomAttributeCount() + 1)
val maxSizeEventAttributes: Map<String, String> = createMapOfSize(limits.getMaxEventAttributeCount())
val tooBigEventAttributes: Map<String, String> = createMapOfSize(limits.getMaxEventAttributeCount() + 1)
val maxSizeSystemAttributes: Map<String, String> = createMapOfSize(limits.getMaxSystemAttributeCount())
val tooBigSystemAttributes: Map<String, String> = createMapOfSize(limits.getMaxSystemAttributeCount() + 1)
val maxSizeCustomEvents: List<EmbraceSpanEvent> = createEventsListOfSize(limits.getMaxCustomEventCount())
val tooBigCustomEvents: List<EmbraceSpanEvent> = createEventsListOfSize(limits.getMaxCustomEventCount() + 1)
val maxSizeSystemEvents: List<EmbraceSpanEvent> = createEventsListOfSize(limits.getMaxSystemEventCount())
val tooBigSystemEvents: List<EmbraceSpanEvent> = createEventsListOfSize(limits.getMaxSystemEventCount() + 1)
