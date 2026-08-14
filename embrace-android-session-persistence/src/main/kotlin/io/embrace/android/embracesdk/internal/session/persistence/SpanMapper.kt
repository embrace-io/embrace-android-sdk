package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent

internal fun Span.toProto(): SpanProto = SpanProto(
    trace_id = traceId.orEmpty(),
    span_id = spanId.orEmpty(),
    parent_span_id = parentSpanId.orEmpty(),
    name = name.orEmpty(),
    start_time_unix_nano = startTimeNanos ?: 0,
    end_time_unix_nano = endTimeNanos,
    status = status?.toProto() ?: SpanProto.Status.UNSET,
    events = events?.map(SpanEvent::toProto).orEmpty(),
    attributes = attributes?.map(Attribute::toProto).orEmpty(),
    links = links?.map(Link::toProto).orEmpty(),
)

internal fun Span.Status.toProto(): SpanProto.Status = when (this) {
    Span.Status.UNSET -> SpanProto.Status.UNSET
    Span.Status.ERROR -> SpanProto.Status.ERROR
    Span.Status.OK -> SpanProto.Status.OK
}

internal fun SpanEvent.toProto(): SpanEventProto = SpanEventProto(
    name = name.orEmpty(),
    time_unix_nano = timestampNanos ?: 0,
    attributes = attributes?.map(Attribute::toProto).orEmpty(),
)

internal fun Attribute.toProto(): AttributeProto = AttributeProto(
    key = key.orEmpty(),
    value_ = data.orEmpty(),
)

internal fun Link.toProto(): LinkProto = LinkProto(
    span_id = spanId.orEmpty(),
    trace_id = traceId.orEmpty(),
    attributes = attributes?.map(Attribute::toProto).orEmpty(),
    is_remote = isRemote ?: false,
)
