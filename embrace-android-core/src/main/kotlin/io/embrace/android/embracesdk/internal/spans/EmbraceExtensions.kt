package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.isAttributeValid
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

internal fun LogRecordBuilder.setFixedAttribute(fixedAttribute: FixedAttribute): LogRecordBuilder {
    setAttribute(fixedAttribute.key.attributeKey, fixedAttribute.value)
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
fun AttributesBuilder.fromMap(attributes: Map<String, String>, internal: Boolean): AttributesBuilder {
    attributes.filter { isAttributeValid(it.key, it.value, internal) || it.key.isValidLongValueAttribute() }.forEach {
        put(it.key, it.value)
    }
    return this
}

fun io.embrace.android.embracesdk.internal.payload.Span.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean {
    return fixedAttribute.value == attributes?.singleOrNull { it.key == fixedAttribute.key.name }?.data
}

fun List<Attribute>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean = any {
    it.key == fixedAttribute.key.name
}

fun EmbraceSpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

fun SpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes?.singleOrNull { it.key == fixedAttribute.key.name }?.data

fun List<Attribute>.findAttributeValue(key: String): String? = singleOrNull { it.key == key }?.data

fun io.embrace.android.embracesdk.internal.payload.Span.getSessionProperty(key: String): String? {
    return attributes?.findAttributeValue(key.toSessionPropertyAttributeName())
}

fun Map<String, String>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    this[fixedAttribute.key.name] == fixedAttribute.value

fun MutableMap<String, String>.setFixedAttribute(fixedAttribute: FixedAttribute): Map<String, String> {
    this[fixedAttribute.key.name] = fixedAttribute.value
    return this
}

fun Map<String, String>.getSessionProperty(key: String): String? = this[key.toSessionPropertyAttributeName()]

fun Map<String, String>.getAttribute(key: AttributeKey<String>): String? = this[key.key]

fun Map<String, String>.getAttribute(key: EmbraceAttributeKey): String? = getAttribute(key.attributeKey)

private fun String.isValidLongValueAttribute(): Boolean = longValueAttributes.contains(this)

private val longValueAttributes: Set<String> = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE.key)

fun StatusCode.toStatus(): io.embrace.android.embracesdk.internal.payload.Span.Status {
    return when (this) {
        StatusCode.UNSET -> io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
        StatusCode.OK -> io.embrace.android.embracesdk.internal.payload.Span.Status.OK
        StatusCode.ERROR -> io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR
    }
}
