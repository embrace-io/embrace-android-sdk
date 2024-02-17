package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.TimeUnit

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

/**
 * Prefix added to [Span] names for all Spans recorded internally by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all [Span] attribute keys for all attributes added by the SDK
 */
private const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

/**
 * Prefix added to all [Span] attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."

/**
 * Attribute name for the monotonically increasing sequence ID given to completed [Span] that expected to sent to the server
 */
private const val SEQUENCE_ID_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "sequence_id"

/**
 * Attribute name for the unique ID assigned to each app instance
 */
private const val PROCESS_IDENTIFIER_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "process_identifier"

/**
 * Creates a new [SpanBuilder] that marks the resulting span as private if [internal] is true
 */
internal fun Tracer.embraceSpanBuilder(
    name: String,
    type: TelemetryType,
    internal: Boolean,
    parent: EmbraceSpan? = null
): EmbraceSpanBuilder {
    val builder = EmbraceSpanBuilder(spanBuilder(name), type, parent)
    return if (internal) {
        builder.makePrivate()
    } else {
        builder
    }
}

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

/**
 * Monotonically increasing ID given to completed [Span] that expected to sent to the server. Can be used to track data loss on the server.
 */
internal fun Span.setSequenceId(id: Long): Span {
    setAttribute(SEQUENCE_ID_ATTRIBUTE_NAME, id)
    return this
}

/**
 * Set the unique ID for this app launch
 */
internal fun Span.setProcessIdentifier(id: String): Span {
    setAttribute(PROCESS_IDENTIFIER_ATTRIBUTE_NAME, id)
    return this
}

internal fun Span.setFixedAttribute(fixedAttribute: FixedAttribute): Span {
    setAttribute(fixedAttribute.key.name, fixedAttribute.value)
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
internal fun String.toEmbraceAttributeName(): String = EMBRACE_ATTRIBUTE_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
internal fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

internal fun EmbraceSpanData.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

internal fun EmbraceSpanEvent.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    fixedAttribute.value == attributes[fixedAttribute.key.name]

internal fun Map<String, String>.hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
    this[fixedAttribute.key.name] == fixedAttribute.value

internal fun MutableMap<String, String>.setFixedAttribute(fixedAttribute: FixedAttribute): Map<String, String> {
    this[fixedAttribute.key.name] = fixedAttribute.value
    return this
}
