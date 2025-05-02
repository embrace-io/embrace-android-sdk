package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData.Companion.fromEventData
import io.embrace.android.embracesdk.internal.payload.Link
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
    links = links.map { Link(it.spanContext.spanId, it.attributes.toEmbracePayload()) }
)
