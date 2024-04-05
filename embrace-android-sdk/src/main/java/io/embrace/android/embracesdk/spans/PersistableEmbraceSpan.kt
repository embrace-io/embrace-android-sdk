package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * An [EmbraceSpan] that has can generate a snapshot of its current state for persistence
 */
internal interface PersistableEmbraceSpan : EmbraceSpan {

    /**
     * Create a snapshot of the current state of the object
     */
    fun snapshot(): Span?

    fun hasEmbraceAttribute(fixedAttribute: FixedAttribute): Boolean

    /**
     * Get the value of the attribute with the given key. Returns null if the attribute does not exist.
     */
    fun getAttributeWithKey(key: String): String?
}
