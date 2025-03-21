package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData.Companion.fromEventData
import io.opentelemetry.sdk.trace.data.SpanData

fun SpanData.toEmbraceSpanData(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeNanos = startEpochNanos,
    endTimeNanos = endEpochNanos,
    status = status.statusCode,
    events = fromEventData(eventDataList = events),
    attributes = attributes.toStringMap(),
    links = links.map { Link(it.spanContext.spanId, it.attributes.toNewPayload()) }
)
