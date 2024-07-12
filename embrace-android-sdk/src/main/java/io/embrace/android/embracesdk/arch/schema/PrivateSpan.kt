package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute

/**
 * Denotes a private span recorded by Embrace for diagnostic or internal usage purposes that is not meant to be consumed directly by
 * users of the SDK, nor is considered part of the public API.
 */
internal object PrivateSpan : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "private")
    override val value: String = "true"
}
