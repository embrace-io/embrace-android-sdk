package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.semconv.ExceptionAttributes

fun EmbraceSpanData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes[embraceAttribute.key.name]

fun EmbraceSpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes[embraceAttribute.key.name]

/**
 * Return the appropriate name used for telemetry created by Embrace given the current value
 */
fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

fun String.isValidLongValueAttribute(): Boolean = longValueAttributes.contains(this)

private val longValueAttributes: Set<String> = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE.key)

fun List<Attribute>.hasEmbraceAttributeKey(embraceAttributeKey: EmbraceAttributeKey): Boolean = any {
    it.key == embraceAttributeKey.name
}

fun List<Attribute>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean = any {
    it.key == embraceAttribute.key.name && it.data == embraceAttribute.value
}

fun List<Attribute>.findAttributeValue(key: String): String? = singleOrNull { it.key == key }?.data

fun Map<String, String>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    this[embraceAttribute.key.name] == embraceAttribute.value

fun Map<String, String>.getAttribute(key: AttributeKey<String>): String? = this[key.key]

fun Map<String, String>.getAttribute(key: EmbraceAttributeKey): String? = this[key.name]

fun MutableMap<String, String>.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): Map<String, String> {
    this[embraceAttribute.key.name] = embraceAttribute.value
    return this
}

fun Severity.toOtelSeverity(): io.opentelemetry.api.logs.Severity = when (this) {
    Severity.INFO -> io.opentelemetry.api.logs.Severity.INFO
    Severity.WARNING -> io.opentelemetry.api.logs.Severity.WARN
    Severity.ERROR -> io.opentelemetry.api.logs.Severity.ERROR
}

fun SpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key.name }?.data

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."
