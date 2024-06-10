package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all attribute keys for all attributes with a specific meaning to the Embrace platform
 */
private const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

/**
 * Prefix added to all Embrace attribute keys that are meant to be internal to Embrace
 */
private const val EMBRACE_PRIVATE_ATTRIBUTE_NAME_PREFIX = "emb.private."

/**
 * Prefix added to all Embrace attribute keys that represent session properties that are set via the SDK
 */
private const val EMBRACE_SESSION_PROPERTY_NAME_PREFIX = "emb.properties."

/**
 * Prefix added to all attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."

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

/**
 * Add the given list of [EmbraceSpanEvent] if they are valid
 */
internal fun Span.addEvents(events: List<EmbraceSpanEvent>): Span {
    events.forEach { event ->
        if (EmbraceSpanEvent.inputsValid(event.name, event.attributes)) {
            addEvent(
                event.name,
                Attributes.builder().fromMap(event.attributes).build(),
                event.timestampNanos,
                TimeUnit.NANOSECONDS
            )
        }
    }
    return this
}

internal fun Span.setEmbraceAttribute(key: EmbraceAttributeKey, value: String): Span {
    setAttribute(key.name, value)
    return this
}

internal fun Span.setFixedAttribute(fixedAttribute: FixedAttribute): Span = setEmbraceAttribute(fixedAttribute.key, fixedAttribute.value)

internal fun LogRecordBuilder.setFixedAttribute(fixedAttribute: FixedAttribute): LogRecordBuilder {
    setAttribute(fixedAttribute.key.attributeKey, fixedAttribute.value)
    return this
}

/**
 * Ends the given [Span], and setting the correct properties per the optional [ErrorCode] passed in. If [errorCode]
 * is not specified, it means the [Span] completed successfully, and no [ErrorCode] will be set.
 */
internal fun Span.endSpan(errorCode: ErrorCode? = null, endTimeMs: Long? = null): Span {
    if (errorCode == null) {
        setStatus(StatusCode.OK)
    } else {
        setStatus(StatusCode.ERROR)
        setFixedAttribute(errorCode.fromErrorCode())
    }

    if (endTimeMs != null) {
        end(endTimeMs, TimeUnit.MILLISECONDS)
    } else {
        end()
    }

    return this
}

/**
 * Returns the attributes as a new Map<String, String>
 */
internal fun Attributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

/**
 * Populate an [AttributesBuilder] with String key-value pairs from a [Map]
 */
internal fun AttributesBuilder.fromMap(attributes: Map<String, String>): AttributesBuilder {
    attributes.filter { EmbraceSpanImpl.attributeValid(it.key, it.value) }.forEach {
        put(it.key, it.value)
    }
    return this
}

/**
 * Return the appropriate name used for telemetry created by Embrace given the current value
 */
internal fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute name given the current string
 */
internal fun String.toEmbraceAttributeName(isPrivate: Boolean = false): String {
    val prefix = if (isPrivate) {
        EMBRACE_PRIVATE_ATTRIBUTE_NAME_PREFIX
    } else {
        EMBRACE_ATTRIBUTE_NAME_PREFIX
    }
    return prefix + this
}

internal fun String.toSessionPropertyAttributeName(): String = EMBRACE_SESSION_PROPERTY_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
internal fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

internal fun SpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    attributes.asMap()[fixedAttribute.key.attributeKey] == fixedAttribute.value

internal fun LogRecordData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    attributes[fixedAttribute.key.attributeKey] == fixedAttribute.value

internal fun EmbraceSpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

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

internal fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): Severity = when (this) {
    io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
    io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
    io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
}
