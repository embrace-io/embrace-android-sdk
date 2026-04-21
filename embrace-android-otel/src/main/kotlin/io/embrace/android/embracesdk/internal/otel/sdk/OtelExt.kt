package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData

fun Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute) =
    setStringAttribute(embraceAttribute.key, embraceAttribute.value)

fun SpanData.toEmbracePayload(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parent.spanId,
    name = name,
    startTimeNanos = startTimestamp,
    endTimeNanos = endTimestamp ?: 0,
    status = status.statusCode,
    events = events.mapNotNull(SpanEventData::toEmbracePayload),
    attributes = attributes.mapValues { it.value.toString() },
    links = links.map(SpanLinkData::toEmbracePayload),
)

fun SpanLinkData.toEmbracePayload(): Link = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.map { Attribute(it.key, it.value.toString()) },
    isRemote = spanContext.isRemote
)

fun SpanEventData.toEmbracePayload(): EmbraceSpanEvent? {
    return EmbraceSpanEvent.create(
        name = name,
        timestampMs = timestamp.nanosToMillis(),
        attributes = attributes.mapValues { it.value.toString() },
    )
}
