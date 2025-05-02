package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.capture.session.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.isAttributeValid
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.utils.isBlankish
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.ExceptionAttributes

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

internal fun LogRecordBuilder.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): LogRecordBuilder {
    setAttribute(embraceAttribute.key.asOtelAttributeKey(), embraceAttribute.value)
    return this
}

internal fun LogRecordBuilder.setAttribute(
    attributeKey: AttributeKey<String>,
    value: String,
    keepBlankishValues: Boolean = true,
): LogRecordBuilder {
    if (keepBlankishValues || !value.isBlankish()) {
        setAttribute(attributeKey, value)
    }
    return this
}

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

fun Span.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean {
    return embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key.name }?.data
}

fun List<Attribute>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean = any {
    it.key == embraceAttribute.key.name
}

fun EmbraceSpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes[embraceAttribute.key.name]

fun SpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key.name }?.data

fun List<Attribute>.findAttributeValue(key: String): String? = singleOrNull { it.key == key }?.data

fun Span.getSessionProperty(key: String): String? {
    return attributes?.findAttributeValue(key.toSessionPropertyAttributeName())
}

@Suppress("UNCHECKED_CAST")
fun Span.getSessionProperties(): Map<String, String> =
    attributes?.filter { it.key != null && it.data != null }?.associate { it.key to it.data } as Map<String, String>

fun Map<String, String>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    this[embraceAttribute.key.name] == embraceAttribute.value

fun MutableMap<String, String>.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): Map<String, String> {
    this[embraceAttribute.key.name] = embraceAttribute.value
    return this
}

fun Map<String, String>.getSessionProperty(key: String): String? = this[key.toSessionPropertyAttributeName()]

fun Map<String, String>.getAttribute(key: AttributeKey<String>): String? = this[key.key]
fun Map<String, String>.getAttribute(key: EmbraceAttributeKey): String? = this[key.name]

private fun String.isValidLongValueAttribute(): Boolean = longValueAttributes.contains(this)

private val longValueAttributes: Set<String> = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE.key)

fun StatusCode.toStatus(): Span.Status {
    return when (this) {
        StatusCode.UNSET -> io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
        StatusCode.OK -> io.embrace.android.embracesdk.internal.payload.Span.Status.OK
        StatusCode.ERROR -> io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR
    }
}

fun OtelLimitsConfig.getMaxTotalAttributeCount() = getMaxSystemAttributeCount() + getMaxCustomAttributeCount()

fun OtelLimitsConfig.getMaxTotalEventCount() = getMaxSystemEventCount() + getMaxCustomEventCount()

fun OtelLimitsConfig.getMaxTotalLinkCount() = getMaxSystemLinkCount() + getMaxCustomLinkCount()
