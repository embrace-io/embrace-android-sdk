package io.embrace.android.embracesdk.internal.payload

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData

internal fun SpanData.toNewPayload() = Span(
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeUnixNano = startEpochNanos,
    endTimeUnixNano = endEpochNanos,
    status = when (status.statusCode) {
        StatusCode.UNSET -> Span.Status.UNSET
        StatusCode.OK -> Span.Status.OK
        StatusCode.ERROR -> Span.Status.ERROR
        else -> Span.Status.UNSET
    },
    events = events.map(EventData::toNewPayload),
    attributes = attributes.toNewPayload()
)

internal fun EventData.toNewPayload() = SpanEvent(
    name = name,
    timeUnixNano = epochNanos,
    attributes = attributes.toNewPayload()
)

internal fun Attributes.toNewPayload(): List<Attribute> =
    asMap().map { (key, value) -> Attribute(key.key, value.toString()) }
