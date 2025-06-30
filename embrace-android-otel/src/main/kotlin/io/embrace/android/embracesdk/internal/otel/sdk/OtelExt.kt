package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData.Companion.fromEventData
import io.embrace.android.embracesdk.internal.otel.toOtelKotlin
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span.Status
import io.embrace.android.embracesdk.internal.utils.isBlankish
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributesBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLinkData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.Span

/**
 * Populate an [AttributesBuilder] with String key-value pairs from a [Map]
 */
fun OtelJavaAttributesBuilder.fromMap(
    attributes: Map<String, String>,
    internal: Boolean,
    limitsValidator: DataValidator
): OtelJavaAttributesBuilder {
    attributes.filter {
        limitsValidator.isAttributeValid(it.key, it.value, internal) || it.key.isValidLongValueAttribute()
    }.forEach {
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

fun OtelJavaLogRecordBuilder.setAttribute(
    attributeKey: OtelJavaAttributeKey<String>,
    value: String,
    keepBlankishValues: Boolean = true,
): OtelJavaLogRecordBuilder {
    if (keepBlankishValues || !value.isBlankish()) {
        setAttribute(attributeKey, value)
    }
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

fun OtelJavaSpanData.toEmbraceSpanData(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeNanos = startEpochNanos,
    endTimeNanos = endEpochNanos,
    status = status.statusCode.toOtelKotlin(),
    events = fromEventData(eventDataList = events),
    attributes = attributes.toStringMap(),
    links = links.map { it.toEmbracePayload() }
)

fun OtelJavaAttributes.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    asMap()[embraceAttribute.key.asOtelAttributeKey()] == embraceAttribute.value

fun OtelJavaStatusCode.toStatus(): Status {
    return when (this) {
        OtelJavaStatusCode.UNSET -> Status.UNSET
        OtelJavaStatusCode.OK -> Status.OK
        OtelJavaStatusCode.ERROR -> Status.ERROR
    }
}
