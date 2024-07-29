package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
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
public fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

public fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): Severity = when (this) {
    io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
    io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
    io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
}

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
internal fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

/**
 * Returns the attributes as a new Map<String, String>
 */
public fun Attributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

public fun EmbraceSpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

internal fun Span.setEmbraceAttribute(key: EmbraceAttributeKey, value: String): Span {
    setAttribute(key.name, value)
    return this
}

public fun Span.setFixedAttribute(fixedAttribute: FixedAttribute): Span = setEmbraceAttribute(fixedAttribute.key, fixedAttribute.value)

internal fun SpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    attributes.asMap()[fixedAttribute.key.attributeKey] == fixedAttribute.value

public fun LogRecordData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    attributes[fixedAttribute.key.attributeKey] == fixedAttribute.value
