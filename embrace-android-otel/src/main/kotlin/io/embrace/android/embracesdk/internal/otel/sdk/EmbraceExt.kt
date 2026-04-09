package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.kotlin.semconv.ExceptionAttributes

fun EmbraceSpanData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes[embraceAttribute.key]

fun EmbraceSpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes[embraceAttribute.key]

/**
 * Return the appropriate name used for telemetry created by Embrace given the current value
 */
fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

fun String.isValidLongValueAttribute(): Boolean = longValueAttributes.contains(this)

private val longValueAttributes: Set<String> = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE)

fun List<Attribute>.hasEmbraceAttributeKey(key: String): Boolean = any {
    it.key == key
}

fun List<Attribute>.hasEmbraceAttributeValue(key: String, value: Any): Boolean = any {
    it.key == key && it.data == value.toString()
}

fun List<Attribute>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean = any {
    it.key == embraceAttribute.key && it.data == embraceAttribute.value
}

fun List<Attribute>.findAttributeValue(key: String): String? = singleOrNull { it.key == key }?.data

fun Map<String, String>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    this[embraceAttribute.key] == embraceAttribute.value

fun MutableMap<String, String>.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): Map<String, String> {
    this[embraceAttribute.key] = embraceAttribute.value
    return this
}

fun MutableMap<String, String>.setEmbraceAttribute(key: String, value: Any): Map<String, String> {
    this[key] = value.toString()
    return this
}

fun SpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key }?.data

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."
