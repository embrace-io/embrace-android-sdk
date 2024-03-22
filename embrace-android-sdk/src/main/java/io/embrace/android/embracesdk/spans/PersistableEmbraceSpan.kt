package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.arch.schema.EmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * An [EmbraceSpan] that has can generate a snapshot of its current state for persistence
 */
internal interface PersistableEmbraceSpan : EmbraceSpan {

    /**
     * Create a snapshot of the current state of the object
     */
    fun snapshot(): Span?

    fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean
}
