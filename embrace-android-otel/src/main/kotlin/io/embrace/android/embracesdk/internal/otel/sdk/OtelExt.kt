package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.data.EventData
import io.embrace.opentelemetry.kotlin.tracing.data.LinkData
import io.embrace.opentelemetry.kotlin.tracing.data.SpanData
import io.embrace.opentelemetry.kotlin.tracing.model.Span

@OptIn(ExperimentalApi::class)
fun Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute) =
    setStringAttribute(embraceAttribute.key.name, embraceAttribute.value)

@OptIn(ExperimentalApi::class)
fun SpanData.toEmbracePayload(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parent.spanId,
    name = name,
    startTimeNanos = startTimestamp,
    endTimeNanos = endTimestamp ?: 0,
    status = status.statusCode,
    events = events.mapNotNull(EventData::toEmbracePayload),
    attributes = attributes.mapValues { it.value.toString() },
    links = links.map(LinkData::toEmbracePayload),
)

@OptIn(ExperimentalApi::class)
fun LinkData.toEmbracePayload(): Link = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.map { Attribute(it.key, it.value.toString()) },
    isRemote = spanContext.isRemote
)

@OptIn(ExperimentalApi::class)
fun EventData.toEmbracePayload(): EmbraceSpanEvent? {
    return EmbraceSpanEvent.create(
        name = name,
        timestampMs = timestamp.nanosToMillis(),
        attributes = attributes.mapValues { it.value.toString() },
    )
}
