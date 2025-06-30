package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContextKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaImplicitContextKeyed
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.tracing.StatusCode

/**
 * An [EmbraceSpan] that has additional functionality to be used internally by the SDK
 */
interface EmbraceSdkSpan : EmbraceSpan, OtelJavaImplicitContextKeyed {

    /**
     * Create a new [Context] object based in this span and its parent's context. This can be used for the parent [Context] for a new span
     * with this span as its parent.
     */
    fun asNewContext(): OtelJavaContext?

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

    /**
     * Set the [StatusCode] and status description of the wrapped Span
     */
    fun setStatus(statusCode: StatusCode, description: String = "")

    fun getStartTimeMs(): Long?

    /**
     * Add a system link to the span that will subjected to a different maximum than typical links.
     */
    fun addSystemLink(
        linkedSpanContext: OtelJavaSpanContext,
        type: LinkType,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean

    override fun storeInContext(context: OtelJavaContext): OtelJavaContext = context.with(embraceSpanContextKey, this)
}

fun OtelJavaContext.getEmbraceSpan(): EmbraceSdkSpan? = get(embraceSpanContextKey)

private val embraceSpanContextKey = OtelJavaContextKey.named<EmbraceSdkSpan>("embrace-span-key")
