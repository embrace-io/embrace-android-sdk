@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.StatusCode

@OptIn(ExperimentalApi::class)
fun EmbraceSpanData.toEmbracePayload(): Span = Span(
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId ?: OtelIds.INVALID_SPAN_ID,
    name = name,
    startTimeNanos = startTimeNanos,
    endTimeNanos = endTimeNanos,
    status = status.toEmbracePayload(),
    events = events.map(EmbraceSpanEvent::toEmbracePayload),
    attributes = attributes.toEmbracePayload(),
    links = links,
)

fun SpanEvent.toEmbracePayload(): EmbraceSpanEvent? = EmbraceSpanEvent.create(
    name = name ?: "",
    timestampMs = (timestampNanos ?: 0).nanosToMillis(),
    attributes = attributes?.toEmbracePayload() ?: emptyMap()
)

fun EmbraceSpanEvent.toEmbracePayload(): SpanEvent = SpanEvent(
    name = name,
    timestampNanos = timestampNanos,
    attributes = attributes.toEmbracePayload()
)

fun Map<String, String>.toEmbracePayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value) }

fun List<Attribute>.toEmbracePayload(): Map<String, String> =
    associate { Pair(it.key ?: "", it.data ?: "") }.filterKeys { it.isNotBlank() }

@OptIn(ExperimentalApi::class)
fun EmbraceLinkData.toEmbracePayload() = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.toEmbracePayload(),
    isRemote = spanContext.isRemote
)

@OptIn(ExperimentalApi::class)
fun Span.toEmbracePayload(): EmbraceSpanData {
    return EmbraceSpanData(
        traceId = traceId ?: "",
        spanId = spanId ?: "",
        parentSpanId = parentSpanId ?: OtelIds.INVALID_SPAN_ID,
        name = name ?: "",
        startTimeNanos = startTimeNanos ?: 0,
        endTimeNanos = endTimeNanos ?: 0L,
        status = when (status) {
            Span.Status.UNSET -> StatusCode.UNSET
            Span.Status.OK -> StatusCode.OK
            Span.Status.ERROR -> StatusCode.ERROR
            else -> StatusCode.UNSET
        },
        events = events?.mapNotNull { it.toEmbracePayload() } ?: emptyList(),
        attributes = attributes?.toEmbracePayload() ?: emptyMap(),
        links = links ?: emptyList()
    )
}
