package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.spans.setFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toStatus
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode

internal fun EmbraceSpanData.toNewPayload() = Span(
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId ?: SpanId.getInvalid(),
    name = name,
    startTimeNanos = startTimeNanos,
    endTimeNanos = endTimeNanos,
    status = status.toStatus(),
    events = events.map(EmbraceSpanEvent::toNewPayload),
    attributes = attributes.toNewPayload()
)

internal fun EmbraceSpanEvent.toNewPayload() = SpanEvent(
    name = name,
    timestampNanos = timestampNanos,
    attributes = attributes.toNewPayload()
)

internal fun SpanEvent.toOldPayload() = EmbraceSpanEvent.create(
    name = name ?: "",
    timestampMs = (timestampNanos ?: 0).nanosToMillis(),
    attributes = attributes?.toOldPayload() ?: emptyMap()
)

internal fun Map<String, String>.toNewPayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value) }

internal fun List<Attribute>.toOldPayload(): Map<String, String> =
    associate { Pair(it.key ?: "", it.data ?: "") }.filterKeys { it.isNotBlank() }

internal fun Span.toOldPayload(): EmbraceSpanData {
    return EmbraceSpanData(
        traceId = traceId ?: "",
        spanId = spanId ?: "",
        parentSpanId = parentSpanId ?: SpanId.getInvalid(),
        name = name ?: "",
        startTimeNanos = startTimeNanos ?: 0,
        endTimeNanos = endTimeNanos ?: 0L,
        status = when (status) {
            Span.Status.UNSET -> StatusCode.UNSET
            Span.Status.OK -> StatusCode.OK
            Span.Status.ERROR -> StatusCode.ERROR
            else -> StatusCode.UNSET
        },
        events = events?.mapNotNull { it.toOldPayload() } ?: emptyList(),
        attributes = attributes?.toOldPayload() ?: emptyMap()
    )
}

internal fun EmbraceSpanData.toFailedSpan(endTimeMs: Long): EmbraceSpanData {
    val newAttributes = mutableMapOf<String, String>().apply {
        setFixedAttribute(ErrorCodeAttribute.Failure)
        if (hasFixedAttribute(EmbType.Ux.Session)) {
            setFixedAttribute(AppTerminationCause.Crash)
        }
    }

    return copy(
        endTimeNanos = endTimeMs.millisToNanos(),
        status = StatusCode.ERROR,
        attributes = attributes.plus(newAttributes)
    )
}

internal fun Span.toFailedSpan(endTimeMs: Long): Span {
    val newAttributes = mutableMapOf<String, String>().apply {
        setFixedAttribute(ErrorCodeAttribute.Failure)
        if (hasFixedAttribute(EmbType.Ux.Session)) {
            setFixedAttribute(AppTerminationCause.Crash)
        }
    }

    return copy(
        endTimeNanos = endTimeMs.millisToNanos(),
        status = Span.Status.ERROR,
        attributes = newAttributes.map { Attribute(it.key, it.value) }.plus(attributes ?: emptyList())
    )
}
