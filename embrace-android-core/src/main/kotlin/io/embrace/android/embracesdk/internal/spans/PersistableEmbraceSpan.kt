package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.ImplicitContextKeyed

/**
 * An [EmbraceSpan] that has can generate a snapshot of its current state for persistence
 */
interface PersistableEmbraceSpan : EmbraceSpan, ImplicitContextKeyed {

    /**
     * Create a new [Context] object based in this span and its parent's context. This can be used for the parent [Context] for a new span
     * with this span as its parent.
     */
    fun asNewContext(): Context?

    /**
     * Create a snapshot of the current state of the object
     */
    fun snapshot(): Span?

    /**
     * Checks to see if the given span has a particular [FixedAttribute]
     */
    fun hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean

    /**
     * Get the value of the attribute with the given key. Returns null if the attribute does not exist.
     */
    fun getAttribute(key: AttributeKey<String>): String?

    /**
     * Remove the system attribute with the given key name
     */
    fun removeAttribute(key: String)

    /**
     * Removes all system events with the given [EmbType]
     */
    fun removeEvents(type: EmbType): Boolean

    /**
     * Set the [StatusCode] and status description of the wrapped Span
     */
    fun setStatus(statusCode: StatusCode, description: String = "")

    override fun storeInContext(context: Context): Context = context.with(embraceSpanContextKey, this)
}

fun Context.getEmbraceSpan(): PersistableEmbraceSpan? = get(embraceSpanContextKey)

private val embraceSpanContextKey = ContextKey.named<PersistableEmbraceSpan>("embrace-span-key")
