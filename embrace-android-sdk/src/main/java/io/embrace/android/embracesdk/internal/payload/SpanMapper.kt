package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.StatusCode

internal fun EmbraceSpanData.toNewPayload() = Span(
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeUnixNano = startTimeNanos,
    endTimeUnixNano = endTimeNanos,
    status = when (status) {
        StatusCode.UNSET -> Span.Status.UNSET
        StatusCode.OK -> Span.Status.OK
        StatusCode.ERROR -> Span.Status.ERROR
        else -> Span.Status.UNSET
    },
    events = events.map(EmbraceSpanEvent::toNewPayload),
    attributes = attributes.toNewPayload()
)

internal fun EmbraceSpanEvent.toNewPayload() = SpanEvent(
    name = name,
    timeUnixNano = timestampNanos,
    attributes = attributes.toNewPayload()
)

internal fun Map<String, String>.toNewPayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value) }
