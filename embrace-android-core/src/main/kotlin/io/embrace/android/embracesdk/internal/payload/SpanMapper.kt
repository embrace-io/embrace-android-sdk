package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.spans.setFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toStatus
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.StatusCode
import io.opentelemetry.api.trace.SpanId

fun EmbraceSpanData.toEmbracePayload(): Span = Span(
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId ?: SpanId.getInvalid(),
    name = name,
    startTimeNanos = startTimeNanos,
    endTimeNanos = endTimeNanos,
    status = status.toStatus(),
    events = events.map(EmbraceSpanEvent::toEmbracePayload),
    attributes = attributes.toEmbracePayload(),
    links = links,
)

fun EmbraceSpanEvent.toEmbracePayload(): SpanEvent = SpanEvent(
    name = name,
    timestampNanos = timestampNanos,
    attributes = attributes.toEmbracePayload()
)

fun SpanEvent.toEmbracePayload(): EmbraceSpanEvent? = EmbraceSpanEvent.create(
    name = name ?: "",
    timestampMs = (timestampNanos ?: 0).nanosToMillis(),
    attributes = attributes?.toEmbracePayload() ?: emptyMap()
)

fun Map<String, String>.toEmbracePayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value) }

fun List<Attribute>.toEmbracePayload(): Map<String, String> =
    associate { Pair(it.key ?: "", it.data ?: "") }.filterKeys { it.isNotBlank() }

fun EmbraceLinkData.toEmbracePayload() = Link(spanContext.spanId, attributes.toEmbracePayload())

fun Span.toEmbracePayload(): EmbraceSpanData {
    return EmbraceSpanData(
        traceId = traceId ?: "",
        spanId = spanId ?: "",
        parentSpanId = parentSpanId ?: SpanId.getInvalid(),
        name = name ?: "",
        startTimeNanos = startTimeNanos ?: 0,
        endTimeNanos = endTimeNanos ?: 0L,
        status = when (status) {
            Span.Status.UNSET -> StatusCode.Unset
            Span.Status.OK -> StatusCode.Ok
            Span.Status.ERROR -> StatusCode.Error(null)
            else -> StatusCode.Unset
        },
        events = events?.mapNotNull { it.toEmbracePayload() } ?: emptyList(),
        attributes = attributes?.toEmbracePayload() ?: emptyMap(),
        links = links ?: emptyList()
    )
}

fun Span.toFailedSpan(endTimeMs: Long): Span {
    val newAttributes = mutableMapOf<String, String>().apply {
        setFixedAttribute(ErrorCodeAttribute.Failure)
        if (hasFixedAttribute(EmbType.Ux.Session)) {
            setFixedAttribute(AppTerminationCause.Crash)
        }
    }

    return copy(
        endTimeNanos = endTimeMs.millisToNanos(),
        parentSpanId = parentSpanId ?: SpanId.getInvalid(),
        status = Span.Status.ERROR,
        attributes = newAttributes.map { Attribute(it.key, it.value) }.plus(attributes ?: emptyList())
    )
}
