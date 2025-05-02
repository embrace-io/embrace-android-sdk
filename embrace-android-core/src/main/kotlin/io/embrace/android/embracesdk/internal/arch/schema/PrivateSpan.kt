package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.FixedAttribute

/**
 * Denotes a private span recorded by Embrace for diagnostic or internal usage purposes that is not meant to be consumed directly by
 * users of the SDK, nor is considered part of the public API.
 */
object PrivateSpan : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "private")
    override val value: String = "true"
}
