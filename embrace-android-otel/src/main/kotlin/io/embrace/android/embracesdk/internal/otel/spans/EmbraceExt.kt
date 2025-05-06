package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."

/**
 * Return the appropriate name used for telemetry created by Embrace given the current value
 */
fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): Severity = when (this) {
    io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
    io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
    io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
}

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

/**
 * Returns the attributes as a new Map<String, String>
 */
fun Attributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

fun EmbraceSpanData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes[embraceAttribute.key.name]

fun Span.setEmbraceAttribute(key: EmbraceAttributeKey, value: String): Span {
    setAttribute(key.name, value)
    return this
}

fun Span.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): Span =
    this@setEmbraceAttribute.setEmbraceAttribute(embraceAttribute.key, embraceAttribute.value)

fun SpanData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    attributes.asMap()[embraceAttribute.key.asOtelAttributeKey()] == embraceAttribute.value

fun LogRecordData.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    attributes[embraceAttribute.key.asOtelAttributeKey()] == embraceAttribute.value
