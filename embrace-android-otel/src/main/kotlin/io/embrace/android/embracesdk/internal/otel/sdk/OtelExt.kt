package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.otel.payload.toPayloadString
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData

fun io.opentelemetry.kotlin.tracing.Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute) =
    setStringAttribute(embraceAttribute.key, embraceAttribute.value)

fun SpanData.toEmbracePayload(): Span = Span(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parent.spanId,
    name = name,
    startTimeNanos = startTimestamp,
    endTimeNanos = endTimestamp ?: 0,
    status = status.toEmbracePayload(),
    events = events.map(SpanEventData::toEmbracePayload),
    // the values of the verbose state rendering are for a customer's exporter: Embrace's servers resolve the link
    attributes = attributes.mapNotNull {
        if (StateKeys.isSpanValueKey(it.key)) null else Attribute(it.key, it.value.toPayloadString())
    },
    links = links.map(SpanLinkData::toEmbracePayload),
)

fun SpanLinkData.toEmbracePayload(): Link = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.map { Attribute(it.key, it.value.toPayloadString()) },
    isRemote = spanContext.isRemote,
)

fun SpanEventData.toEmbracePayload(): SpanEvent = SpanEvent(
    name = name,
    timestampNanos = timestamp,
    attributes = attributes.map { Attribute(it.key, it.value.toPayloadString()) },
)
