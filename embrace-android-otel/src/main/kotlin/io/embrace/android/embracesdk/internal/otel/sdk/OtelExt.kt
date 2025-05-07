package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.internal.otel.config.isAttributeValid
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData.Companion.fromEventData
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.utils.isBlankish
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Populate an [AttributesBuilder] with String key-value pairs from a [Map]
 */
fun AttributesBuilder.fromMap(
    attributes: Map<String, String>,
    internal: Boolean,
    limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
): AttributesBuilder {
    attributes.filter {
        limits.isAttributeValid(it.key, it.value, internal) || it.key.isValidLongValueAttribute()
    }.forEach {
        put(it.key, it.value)
    }
    return this
}

/**
 * Returns the attributes as a new Map<String, String>
 */
fun Attributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

fun LinkData.toEmbracePayload() = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.toEmbracePayload(),
    isRemote = spanContext.isRemote
)

fun LogRecordBuilder.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): LogRecordBuilder {
    setAttribute(embraceAttribute.key.asOtelAttributeKey(), embraceAttribute.value)
    return this
}

fun LogRecordBuilder.setAttribute(
    attributeKey: AttributeKey<String>,
    value: String,
    keepBlankishValues: Boolean = true,
): LogRecordBuilder {
    if (keepBlankishValues || !value.isBlankish()) {
        setAttribute(attributeKey, value)
    }
    return this
}

fun LogRecordData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    attributes[embraceAttribute.key.asOtelAttributeKey()] == embraceAttribute.value

fun Span.setEmbraceAttribute(key: EmbraceAttributeKey, value: String): Span {
    setAttribute(key.name, value)
    return this
}

fun Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): Span =
    this@setEmbraceAttribute.setEmbraceAttribute(embraceAttribute.key, embraceAttribute.value)

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
    links = links.map { it.toEmbracePayload() }
)

fun SpanData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    attributes.asMap()[embraceAttribute.key.asOtelAttributeKey()] == embraceAttribute.value

fun StatusCode.toStatus(): io.embrace.android.embracesdk.internal.payload.Span.Status {
    return when (this) {
        StatusCode.UNSET -> io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
        StatusCode.OK -> io.embrace.android.embracesdk.internal.payload.Span.Status.OK
        StatusCode.ERROR -> io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR
    }
}
