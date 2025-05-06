package io.embrace.android.embracesdk.internal.otel.schema

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey

/**
 * Denotes a private span recorded by Embrace for diagnostic or internal usage purposes that is not meant to be consumed directly by
 * users of the SDK, nor is considered part of the public API.
 */
object PrivateSpan : EmbraceAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey.create(id = "private")
    override val value: String = "true"
}
