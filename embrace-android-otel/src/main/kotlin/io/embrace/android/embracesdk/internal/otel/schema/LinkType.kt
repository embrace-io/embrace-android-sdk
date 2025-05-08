package io.embrace.android.embracesdk.internal.otel.schema

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey

sealed class LinkType(
    override val value: String,
) : EmbraceAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey.create(id = "link_type")

    object PreviousSession : LinkType("PREV_SESSION")
}
