package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.ImplicitContextKeyed

/**
 * An [EmbraceSpan] that has can generate a snapshot of its current state for persistence
 */
public interface PersistableEmbraceSpan : EmbraceSpan, ImplicitContextKeyed {

    /**
     * Create a new [Context] object based in this span and its parent's context. This can be used for the parent [Context] for a new span
     * with this span as its parent.
     */
    public fun asNewContext(): Context?

    /**
     * Create a snapshot of the current state of the object
     */
    public fun snapshot(): Span?

    /**
     * Checks to see if the given span has a particular [FixedAttribute]
     */
    public fun hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean

    /**
     * Get the value of the attribute with the given key. Returns null if the attribute does not exist.
     */
    public fun getSystemAttribute(key: EmbraceAttributeKey): String?

    /**
     * Set the value of the attribute with the given key, overwriting the original value if it's already set
     */
    public fun setSystemAttribute(key: EmbraceAttributeKey, value: String)

    /**
     * Remove the custom attribute with the given key name
     */
    public fun removeCustomAttribute(key: String): Boolean

    /**
     * Removes all events with the given [EmbType]
     */
    public fun removeEvents(type: EmbType): Boolean

    /**
     * Set the [StatusCode] and status description of the wrapped Span
     */
    public fun setStatus(statusCode: StatusCode, description: String = "")

    override fun storeInContext(context: Context): Context = context.with(embraceSpanContextKey, this)
}

public fun Context.getEmbraceSpan(): PersistableEmbraceSpan? = get(embraceSpanContextKey)

private val embraceSpanContextKey = ContextKey.named<PersistableEmbraceSpan>("embrace-span-key")
