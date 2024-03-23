package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.spans.setEmbraceAttribute
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.SpanId
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

internal fun SpanEvent.toOldPayload() = EmbraceSpanEvent(
    name = name ?: "",
    timestampNanos = timeUnixNano ?: 0,
    attributes = attributes?.toOldPayload() ?: emptyMap()
)

internal fun Map<String, String>.toNewPayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value) }

internal fun List<Attribute>.toOldPayload(): Map<String, String> =
    associate { Pair(it.key ?: "", it.data ?: "") }.filterKeys { it.isNotBlank() }

internal fun Span.toFailedSpan(endTimeMs: Long): EmbraceSpanData {
    val newAttributes = attributes?.toOldPayload()?.toMutableMap()?.apply {
        setEmbraceAttribute(ErrorCodeAttribute.Failure)
        if (hasEmbraceAttribute(EmbType.Ux.Session)) {
            setEmbraceAttribute(AppTerminationCause.Crash)
        }
    } ?: emptyMap()

    return EmbraceSpanData(
        traceId = traceId ?: "",
        spanId = spanId ?: "",
        parentSpanId = parentSpanId ?: SpanId.getInvalid(),
        name = name ?: "",
        startTimeNanos = startTimeUnixNano ?: 0,
        endTimeNanos = endTimeMs.millisToNanos(),
        status = StatusCode.ERROR,
        events = events?.map { it.toOldPayload() } ?: emptyList(),
        attributes = newAttributes
    )
}
