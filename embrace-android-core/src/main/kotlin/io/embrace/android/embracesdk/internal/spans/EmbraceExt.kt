package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.api.common.Attributes
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
internal fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): SeverityNumber = when (this) {
    io.embrace.android.embracesdk.Severity.INFO -> SeverityNumber.INFO
    io.embrace.android.embracesdk.Severity.WARNING -> SeverityNumber.WARN
    io.embrace.android.embracesdk.Severity.ERROR -> SeverityNumber.ERROR
}

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
internal fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

/**
 * Returns the attributes as a new Map<String, String>
 */
internal fun Attributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

fun EmbraceSpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

internal fun Span.setEmbraceAttribute(key: EmbraceAttributeKey, value: String): Span {
    setAttribute(key.name, value)
    return this
}

internal fun Span.setFixedAttribute(fixedAttribute: FixedAttribute): Span =
    setEmbraceAttribute(fixedAttribute.key, fixedAttribute.value)

internal fun SpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    attributes.asMap()[fixedAttribute.key.attributeKey] == fixedAttribute.value

fun LogRecordData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    attributes[fixedAttribute.key.attributeKey] == fixedAttribute.value
