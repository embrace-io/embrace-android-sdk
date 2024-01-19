package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes.Attribute
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

/**
 * Prefix added to [Span] names for all Spans recorded internally by the SDK
 */
private const val EMBRACE_SPAN_NAME_PREFIX = "emb-"

/**
 * Prefix added to all [Span] attribute keys for all attributes added by the SDK
 */
private const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

/**
 * Prefix added to all [Span] attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."

/**
 * Prefix added to all [Span] attribute keys for all storage attributes added by the SDK
 */
private const val EMBRACE_STORAGE_TELEMETRY_ATTRIBUTE_NAME_PREFIX = "emb.storage."

/**
 * Attribute name for the monotonically increasing sequence ID given to completed [Span] that expected to sent to the server
 */
private const val SEQUENCE_ID_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "sequence_id"

/**
 * Denotes an important span to be listed in the Spans listing page in the UI. Currently defined as any spans that are the root of a trace
 */
private const val KEY_SPAN_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "key"

/**
 * Denotes a private span logged by Embrace for diagnostic purposes and should not be displayed to customers in the dashboard, but
 * should be shown to Embrace employees.
 */
private const val PRIVATE_SPAN_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "private"

/**
 * Creates a new [SpanBuilder] with the correctly prefixed name, to be used for recording Spans in the SDK internally
 */
internal fun Tracer.embraceSpanBuilder(name: String, internal: Boolean): SpanBuilder {
    return if (internal) {
        spanBuilder(EMBRACE_SPAN_NAME_PREFIX + name).makePrivate()
    } else {
        spanBuilder(name)
    }
}

/**
 * Sets and returns the [EmbraceAttributes.Type] attribute for the given [SpanBuilder]
 */
internal fun SpanBuilder.setType(value: EmbraceAttributes.Type): SpanBuilder {
    setAttribute(value.keyName(), value.toString())
    return this
}

/**
 * Mark the span generated by this builder as an important one, to be listed in the Spans listing page in the UI. It is currently used
 * for any spans that are the root of a trace.
 */
internal fun SpanBuilder.makeKey(): SpanBuilder {
    setAttribute(KEY_SPAN_ATTRIBUTE_NAME, true)
    return this
}

/**
 * Mark the span generated by this builder as a private span logged by Embrace for diagnostic purposes and that should not be displayed
 * to customers in the dashboard, but should be shown to Embrace employees.
 */
internal fun SpanBuilder.makePrivate(): SpanBuilder {
    setAttribute(PRIVATE_SPAN_ATTRIBUTE_NAME, true)
    return this
}

/**
 * Extract the parent span from an [EmbraceSpan] and set it as the parent. It's a no-op if the parent has not been started yet.
 */
internal fun SpanBuilder.updateParent(parent: EmbraceSpan?): SpanBuilder {
    if (parent == null) {
        makeKey()
    } else if (parent is EmbraceSpanImpl) {
        parent.wrappedSpan()?.let {
            setParent(Context.current().with(it))
        }
    }
    return this
}

/**
 * Allow a [SpanBuilder] to take in a lambda around which a span will be created for its execution
 */
internal fun <T> SpanBuilder.record(code: () -> T): T {
    val returnValue: T
    var span: Span? = null

    try {
        span = startSpan()
        returnValue = code()
        span.endSpan()
    } catch (t: Throwable) {
        span?.endSpan(ErrorCode.FAILURE)
        throw t
    }

    return returnValue
}

/**
 * Monotonically increasing ID given to completed [Span] that expected to sent to the server. Can be used to track data loss on the server.
 */
internal fun Span.setSequenceId(id: Long): Span {
    setAttribute(SEQUENCE_ID_ATTRIBUTE_NAME, id)
    return this
}

/**
 * Ends the given [Span], and setting the correct properties per the optional [ErrorCode] passed in. If [errorCode]
 * is not specified, it means the [Span] completed successfully, and no [ErrorCode] will be set.
 */
internal fun Span.endSpan(errorCode: ErrorCode? = null, endTimeNanos: Long? = null): Span {
    if (errorCode == null) {
        setStatus(StatusCode.OK)
    } else {
        setStatus(StatusCode.ERROR)
        setAttribute(errorCode.keyName(), errorCode.toString())
    }

    if (endTimeNanos != null) {
        end(endTimeNanos, TimeUnit.NANOSECONDS)
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
 * Returns true if the Span is a Key Span
 */
internal fun EmbraceSpanData.isKey(): Boolean = attributes[KEY_SPAN_ATTRIBUTE_NAME] == true.toString()

/**
 * Returns true if the Span is private
 */
internal fun EmbraceSpanData.isPrivate(): Boolean = attributes[PRIVATE_SPAN_ATTRIBUTE_NAME] == true.toString()

/**
 * Return the appropriate internal Embrace Span name given the current value
 */
internal fun String.toEmbraceSpanName(): String = EMBRACE_SPAN_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute name given the current string
 */
internal fun String.toEmbraceAttributeName(): String = EMBRACE_ATTRIBUTE_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
internal fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this

/**
 * Return the appropriate internal Embrace storage attribute name given the current string
 */
internal fun String.toEmbraceStorageAttributeName(): String = EMBRACE_STORAGE_TELEMETRY_ATTRIBUTE_NAME_PREFIX + this

/**
 * Contains the set of attributes (i.e. implementers of the [Attribute] interface) set on a [Span] by the SDK that has special meaning
 * in the Embrace world. Each enum defines the attribute name used in the [Span] and specifies the set of valid values it can be set to.
 */
internal object EmbraceAttributes {

    /**
     * Attribute to categorize a [Span] and give it a distinct semantic meaning. Spans of each [Type] may be treated differently by the
     * backend and can be expected to contain a set of attributes to further flesh out the given semantic meanings.
     */
    internal enum class Type : Attribute {
        /**
         * Spans that model an Embrace session or background activity.
         */
        SESSION,

        /**
         * A [Span] created by an SDK user to measure the performance of an operation
         */
        PERFORMANCE;

        override val canonicalName = "type"
    }

    /**
     * The reason for the termination of a process span
     */
    internal enum class AppTerminationCause : Attribute {
        CRASH,
        USER_TERMINATION,
        UNKNOWN;

        override val canonicalName: String = "termination_cause"
    }

    /**
     * Denotes an attribute added by the SDK with a restricted set of valid values
     */
    internal interface Attribute {

        /**
         * The name used to identify this [Attribute]
         */
        val canonicalName: String

        /**
         * The name used as the key for the [Attribute] in the attributes map
         */
        fun keyName(): String = EMBRACE_ATTRIBUTE_NAME_PREFIX + canonicalName
    }
}
