package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
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

fun List<Attribute>.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean = any {
    it.key == embraceAttribute.key.name
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

fun OtelLimitsConfig.getMaxTotalAttributeCount() = getMaxSystemAttributeCount() + getMaxCustomAttributeCount()

fun OtelLimitsConfig.getMaxTotalEventCount() = getMaxSystemEventCount() + getMaxCustomEventCount()

fun OtelLimitsConfig.getMaxTotalLinkCount() = getMaxSystemLinkCount() + getMaxCustomLinkCount()

fun Severity.toOtelSeverity(): io.opentelemetry.api.logs.Severity = when (this) {
    Severity.INFO -> io.opentelemetry.api.logs.Severity.INFO
    Severity.WARNING -> io.opentelemetry.api.logs.Severity.WARN
    Severity.ERROR -> io.opentelemetry.api.logs.Severity.ERROR
}

fun Span.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean {
    return embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key.name }?.data
}

fun SpanEvent.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
    embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key.name }?.data

fun Span.toFailedSpan(endTimeMs: Long): Span {
    val newAttributes = mutableMapOf<String, String>().apply {
        setEmbraceAttribute(ErrorCodeAttribute.Failure)
        if (hasEmbraceAttribute(EmbType.Ux.Session)) {
            setEmbraceAttribute(AppTerminationCause.Crash)
        }
    }

    return copy(
        endTimeNanos = endTimeMs.millisToNanos(),
        parentSpanId = parentSpanId ?: SpanId.getInvalid(),
        status = Span.Status.ERROR,
        attributes = newAttributes.map { Attribute(it.key, it.value) }.plus(attributes ?: emptyList())
    )
}

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."

private val longValueAttributes: Set<String> = setOf(ExceptionAttributes.EXCEPTION_STACKTRACE.key)
