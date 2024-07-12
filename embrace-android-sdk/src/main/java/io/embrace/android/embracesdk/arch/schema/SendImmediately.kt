package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute

internal object SendImmediately : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "send_immediately")
    override val value: String = "true"
}
