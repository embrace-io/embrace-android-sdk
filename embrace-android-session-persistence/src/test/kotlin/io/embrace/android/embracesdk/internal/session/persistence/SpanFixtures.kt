package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent

internal val fullyPopulatedSpan = Span(
    traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
    spanId = "aaaaaaaaaaaaaaa1",
    parentSpanId = "aaaaaaaaaaaaaaa2",
    name = "emb-session",
    startTimeNanos = 1726739283136000000L,
    endTimeNanos = 1726739284136000000L,
    status = Span.Status.OK,
    events = listOf(
        SpanEvent(
            name = "event",
            timestampNanos = 1726739283500000000L,
            attributes = listOf(Attribute(key = "event.key", data = "event.value")),
        ),
    ),
    attributes = listOf(
        Attribute(key = "emb.heartbeat_time_unix_nano", data = "1726739284136000000"),
        Attribute(key = "emb.terminated", data = "true"),
    ),
    links = listOf(
        Link(
            spanId = "aaaaaaaaaaaaaaa3",
            traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c12",
            attributes = listOf(Attribute(key = "link.key", data = "link.value")),
            isRemote = true,
        ),
    ),
)

internal val fullyPopulatedSpanProto = SpanProto(
    trace_id = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
    span_id = "aaaaaaaaaaaaaaa1",
    parent_span_id = "aaaaaaaaaaaaaaa2",
    name = "emb-session",
    start_time_unix_nano = 1726739283136000000L,
    end_time_unix_nano = 1726739284136000000L,
    status = SpanProto.Status.OK,
    events = listOf(
        SpanEventProto(
            name = "event",
            time_unix_nano = 1726739283500000000L,
            attributes = listOf(AttributeProto(key = "event.key", value_ = "event.value")),
        ),
    ),
    attributes = listOf(
        AttributeProto(key = "emb.heartbeat_time_unix_nano", value_ = "1726739284136000000"),
        AttributeProto(key = "emb.terminated", value_ = "true"),
    ),
    links = listOf(
        LinkProto(
            span_id = "aaaaaaaaaaaaaaa3",
            trace_id = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c12",
            attributes = listOf(AttributeProto(key = "link.key", value_ = "link.value")),
            is_remote = true,
        ),
    ),
)

internal val fullyPopulatedSessionSpanProto = SessionSpan(
    format_version = FORMAT_VERSION,
    span = fullyPopulatedSpanProto,
)
