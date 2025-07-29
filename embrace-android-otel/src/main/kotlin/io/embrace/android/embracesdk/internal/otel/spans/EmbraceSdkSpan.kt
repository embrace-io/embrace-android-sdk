@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaImplicitContextKeyed
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.k2j.context.root
import io.embrace.opentelemetry.kotlin.k2j.tracing.toOtelJava
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

/**
 * An [EmbraceSpan] that has additional functionality to be used internally by the SDK
 */
@OptIn(ExperimentalApi::class)
interface EmbraceSdkSpan : EmbraceSpan, OtelJavaImplicitContextKeyed {

    /**
     * Create a new context object based in this span and its parent's context. This can be used for the parent context for a new span
     * with this span as its parent.
     */
    fun asNewContext(): Context?

    /**
     * Create a snapshot of the current state of the object
     */
    fun snapshot(): Span?

    /**
     * Checks to see if the given span has a particular [EmbraceAttribute]
     */
    fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean

    /**
     * Get the value of the attribute with the given key. Returns null if the attribute does not exist.
     */
    fun getSystemAttribute(key: String): String?

    /**
     * Set the value of the attribute with the given key, overwriting the original value if it's already set
     */
    fun setSystemAttribute(key: String, value: String)

    /**
     * Add the given key value pair as a system attribute to ths span
     */
    fun addSystemAttribute(key: String, value: String)

    /**
     * Remove the system attribute with the given key name
     */
    fun removeSystemAttribute(key: String)

    /**
     * Add a system event to the span that will subjected to a different maximum than typical span events.
     */
    fun addSystemEvent(
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>?,
    ): Boolean

    /**
     * Removes all system events with the given [EmbType]
     */
    fun removeSystemEvents(type: EmbType): Boolean

    fun getStartTimeMs(): Long?

    /**
     * Add a system link to the span that will subjected to a different maximum than typical links.
     */
    @OptIn(ExperimentalApi::class)
    fun addSystemLink(
        linkedSpanContext: SpanContext,
        type: LinkType,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean

    override fun storeInContext(context: OtelJavaContext): OtelJavaContext = context.with(embraceSpanContextKey.toOtelJava(), this)

    /**
     * Returns a read-only view of the attributes
     */
    fun attributes(): Map<String, Any>

    /**
     * Retrieves the span name
     */
    fun name(): String

    /**
     * Retrieves the span kind
     */
    val spanKind: SpanKind

    /**
     * The span status
     */
    var status: StatusCode

    /**
     * Retrieves the span events
     */
    fun events(): List<SpanEvent>

    /**
     * Retrieves the span links
     */
    fun links(): List<Link>
}

fun Context.getEmbraceSpan(): EmbraceSdkSpan? = get(embraceSpanContextKey)

fun EmbraceSdkSpan.createContext(): Context {
    val newParentContext = asNewContext() ?: Context.root()
    return newParentContext.set(embraceSpanContextKey, this)
}

private val embraceSpanContextKey = Context.root().createKey<EmbraceSdkSpan>("embrace-span-key")
