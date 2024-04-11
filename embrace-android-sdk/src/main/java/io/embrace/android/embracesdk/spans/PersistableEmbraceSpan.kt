package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
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

    /**
     * Checks to see if the given span has a particular [FixedAttribute]
     */
    fun hasEmbraceAttribute(fixedAttribute: FixedAttribute): Boolean

    /**
     * Get the value of the attribute with the given key. Returns null if the attribute does not exist.
     */
    fun getAttribute(key: EmbraceAttributeKey): String?

    /**
     * Remove the custom attribute with the given key name
     */
    fun removeCustomAttribute(key: String): Boolean
}
