package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableLink
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpanEvent
import io.embrace.opentelemetry.kotlin.tracing.model.Span

/**
 * Creates a sanitized map of attributes.
 */
fun Map<String, String>.sanitizeAttributesMap(
    internal: Boolean,
    limitsValidator: DataValidator,
): Map<String, String> {
    return filter {
        limitsValidator.isAttributeValid(it.key, it.value, internal) || it.key.isValidLongValueAttribute()
    }.toMap()
}

@OptIn(ExperimentalApi::class)
fun Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute) =
    setStringAttribute(embraceAttribute.key.name, embraceAttribute.value)

@OptIn(ExperimentalApi::class)
fun ReadableSpan.toEmbracePayload(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parent?.spanId,
    name = name,
    startTimeNanos = startTimestamp,
    endTimeNanos = endTimestamp ?: 0,
    status = status,
    events = events.mapNotNull(ReadableSpanEvent::toEmbracePayload),
    attributes = attributes.mapValues { it.value.toString() },
    links = links.map(ReadableLink::toEmbracePayload),
)

@OptIn(ExperimentalApi::class)
fun ReadableLink.toEmbracePayload(): Link = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.map { Attribute(it.key, it.value.toString()) },
    isRemote = spanContext.isRemote
)

@OptIn(ExperimentalApi::class)
fun ReadableSpanEvent.toEmbracePayload(): EmbraceSpanEvent? {
    return EmbraceSpanEvent.create(
        name = name,
        timestampMs = timestamp.nanosToMillis(),
        attributes = attributes.mapValues { it.value.toString() },
    )
}
