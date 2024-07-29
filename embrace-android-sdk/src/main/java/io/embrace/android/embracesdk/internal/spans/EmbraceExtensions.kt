package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.semconv.ExceptionAttributes

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

/**
 * Creates a new [SpanBuilder] that marks the resulting span as private if [internal] is true
 */
internal fun Tracer.embraceSpanBuilder(
    name: String,
    type: TelemetryType,
    internal: Boolean,
    private: Boolean,
    parent: EmbraceSpan? = null
): EmbraceSpanBuilder = EmbraceSpanBuilder(
    tracer = this,
    name = name,
    telemetryType = type,
    internal = internal,
    private = private,
    parentSpan = parent,
)

internal fun LogRecordBuilder.setFixedAttribute(fixedAttribute: FixedAttribute): LogRecordBuilder {
    setAttribute(fixedAttribute.key.attributeKey, fixedAttribute.value)
    return this
}

/**
 * Populate an [AttributesBuilder] with String key-value pairs from a [Map]
 */
internal fun AttributesBuilder.fromMap(attributes: Map<String, String>): AttributesBuilder {
    attributes.filter { EmbraceSpanImpl.attributeValid(it.key, it.value) || it.key.isValidLongValueAttribute() }.forEach {
        put(it.key, it.value)
    }
    return this
}

internal fun io.embrace.android.embracesdk.internal.payload.Span.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean {
    return fixedAttribute.value == attributes?.singleOrNull { it.key == fixedAttribute.key.name }?.data
}

internal fun List<Attribute>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean = any {
    it.key == fixedAttribute.key.name
}

internal fun EmbraceSpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

internal fun SpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes?.singleOrNull { it.key == fixedAttribute.key.name }?.data

internal fun List<Attribute>.findAttributeValue(key: String): String? = singleOrNull { it.key == key }?.data

internal fun io.embrace.android.embracesdk.internal.payload.Span.getSessionProperty(key: String): String? {
    return attributes?.findAttributeValue(key.toSessionPropertyAttributeName())
}

internal fun Map<String, String>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    this[fixedAttribute.key.name] == fixedAttribute.value

internal fun MutableMap<String, String>.setFixedAttribute(fixedAttribute: FixedAttribute): Map<String, String> {
    this[fixedAttribute.key.name] = fixedAttribute.value
    return this
}

internal fun Map<String, String>.getSessionProperty(key: String): String? = this[key.toSessionPropertyAttributeName()]

internal fun Map<String, String>.getAttribute(key: AttributeKey<String>): String? = this[key.key]

internal fun Map<String, String>.getAttribute(key: EmbraceAttributeKey): String? = getAttribute(key.attributeKey)

internal fun String.isValidLongValueAttribute() = longValueAttributes.contains(this)

internal val longValueAttributes = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE.key)

internal fun StatusCode.toStatus(): io.embrace.android.embracesdk.internal.payload.Span.Status {
    return when (this) {
        StatusCode.UNSET -> io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
        StatusCode.OK -> io.embrace.android.embracesdk.internal.payload.Span.Status.OK
        StatusCode.ERROR -> io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR
    }
}
