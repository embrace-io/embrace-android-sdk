package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.ExceptionAttributes

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

public fun LogRecordBuilder.setFixedAttribute(fixedAttribute: FixedAttribute): LogRecordBuilder {
    setAttribute(fixedAttribute.key.attributeKey, fixedAttribute.value)
    return this
}

/**
 * Populate an [AttributesBuilder] with String key-value pairs from a [Map]
 */
public fun AttributesBuilder.fromMap(attributes: Map<String, String>): AttributesBuilder {
    attributes.filter { EmbraceSpanImpl.attributeValid(it.key, it.value) || it.key.isValidLongValueAttribute() }.forEach {
        put(it.key, it.value)
    }
    return this
}

public fun io.embrace.android.embracesdk.internal.payload.Span.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean {
    return fixedAttribute.value == attributes?.singleOrNull { it.key == fixedAttribute.key.name }?.data
}

public fun List<Attribute>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean = any {
    it.key == fixedAttribute.key.name
}

public fun EmbraceSpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

public fun SpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes?.singleOrNull { it.key == fixedAttribute.key.name }?.data

public fun List<Attribute>.findAttributeValue(key: String): String? = singleOrNull { it.key == key }?.data

public fun io.embrace.android.embracesdk.internal.payload.Span.getSessionProperty(key: String): String? {
    return attributes?.findAttributeValue(key.toSessionPropertyAttributeName())
}

public fun Map<String, String>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    this[fixedAttribute.key.name] == fixedAttribute.value

public fun MutableMap<String, String>.setFixedAttribute(fixedAttribute: FixedAttribute): Map<String, String> {
    this[fixedAttribute.key.name] = fixedAttribute.value
    return this
}

public fun Map<String, String>.getSessionProperty(key: String): String? = this[key.toSessionPropertyAttributeName()]

public fun Map<String, String>.getAttribute(key: AttributeKey<String>): String? = this[key.key]

public fun Map<String, String>.getAttribute(key: EmbraceAttributeKey): String? = getAttribute(key.attributeKey)

public fun String.isValidLongValueAttribute(): Boolean = longValueAttributes.contains(this)

public val longValueAttributes: Set<String> = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE.key)

public fun StatusCode.toStatus(): io.embrace.android.embracesdk.internal.payload.Span.Status {
    return when (this) {
        StatusCode.UNSET -> io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
        StatusCode.OK -> io.embrace.android.embracesdk.internal.payload.Span.Status.OK
        StatusCode.ERROR -> io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR
    }
}
