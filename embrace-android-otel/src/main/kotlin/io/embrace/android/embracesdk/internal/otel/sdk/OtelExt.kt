package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributesBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaEventData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLinkData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.k2j.tracing.convertToOtelKotlin
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableLink
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpanEvent
import io.embrace.opentelemetry.kotlin.tracing.model.Span

/**
 * Populate an AttributesBuilder with String key-value pairs from a [Map]
 */
fun OtelJavaAttributesBuilder.fromMap(
    attributes: Map<String, String>,
    internal: Boolean,
    dataValidator: DataValidator,
): OtelJavaAttributesBuilder {
    dataValidator.truncateAttributes(attributes, internal).forEach {
        put(it.key, it.value)
    }
    return this
}

/**
 * Returns the attributes as a new Map<String, String>
 */
fun OtelJavaAttributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

fun OtelJavaLinkData.toEmbracePayload() = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.toEmbracePayload(),
    isRemote = spanContext.isRemote
)

fun OtelJavaLogRecordBuilder.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): OtelJavaLogRecordBuilder {
    setAttribute(embraceAttribute.key.asOtelAttributeKey(), embraceAttribute.value)
    return this
}

fun OtelJavaSpan.setEmbraceAttribute(key: EmbraceAttributeKey, value: String): OtelJavaSpan {
    setAttribute(key.name, value)
    return this
}

fun OtelJavaSpan.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): OtelJavaSpan =
    this@setEmbraceAttribute.setEmbraceAttribute(embraceAttribute.key, embraceAttribute.value)

@OptIn(ExperimentalApi::class)
fun Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute) =
    setStringAttribute(embraceAttribute.key.name, embraceAttribute.value)

@OptIn(ExperimentalApi::class)
fun OtelJavaSpanData.toEmbraceSpanData(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeNanos = startEpochNanos,
    endTimeNanos = endEpochNanos,
    status = status.statusCode.convertToOtelKotlin(),
    events = events?.mapNotNull { it.toEmbracePayload() } ?: emptyList(),
    attributes = attributes.toStringMap(),
    links = links.map { it.toEmbracePayload() }
)

private fun OtelJavaEventData.toEmbracePayload(): EmbraceSpanEvent? {
    return EmbraceSpanEvent.create(
        name = name,
        timestampMs = epochNanos.nanosToMillis(),
        attributes = attributes.toStringMap(),
    )
}

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

fun OtelJavaAttributes.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    asMap()[embraceAttribute.key.asOtelAttributeKey()] == embraceAttribute.value
