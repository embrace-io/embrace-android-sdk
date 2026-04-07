package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes

/**
 * Denotes a private span recorded by Embrace for diagnostic or internal usage purposes that is not meant to be consumed directly by
 * users of the SDK, nor is considered part of the public API.
 */
object PrivateSpan : EmbraceAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(EmbSpanAttributes.EMB_PRIVATE)
    override val value: String = "true"
}
