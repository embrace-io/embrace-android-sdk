@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.context.ContextKey
import io.embrace.opentelemetry.kotlin.tracing.data.StatusData
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

/**
 * An [EmbraceSpan] that has additional functionality to be used internally by the SDK
 */
@OptIn(ExperimentalApi::class)
interface EmbraceSdkSpan : EmbraceSpan {

    /**
     * Create a new context object based in this span and its parent's context. This can be used for the parent context for a new span
     * with this span as its parent.
     */
    fun asNewContext(): Context?

    /**
     * Get the W3C Traceparent representation for the span, which uniquely identifies it, if it has started, or null otherwise.
     */
    fun asW3cTraceParent(): String?

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
    var status: StatusData

    /**
     * Retrieves the span events
     */
    fun events(): List<SpanEvent>

    /**
     * Retrieves the span links
     */
    fun links(): List<Link>
}

private val lock = Any()
private var embraceSpanContextKey: ContextKey<EmbraceSdkSpan>? = null

fun Context.getEmbraceSpan(openTelemetry: OpenTelemetry): EmbraceSdkSpan? = get(getOrCreateSpanKey(openTelemetry))

fun EmbraceSdkSpan.createContext(openTelemetry: OpenTelemetry): Context {
    val newParentContext = asNewContext() ?: openTelemetry.contextFactory.root()
    return newParentContext.set(getOrCreateSpanKey(openTelemetry), this)
}

fun getOrCreateSpanKey(openTelemetry: OpenTelemetry): ContextKey<EmbraceSdkSpan> {
    if (embraceSpanContextKey == null) {
        synchronized(lock) {
            if (embraceSpanContextKey == null) {
                embraceSpanContextKey = openTelemetry.contextFactory.root().createKey("embrace-span-key")
            }
        }
    }
    return embraceSpanContextKey ?: error("Failed to create context key")
}
