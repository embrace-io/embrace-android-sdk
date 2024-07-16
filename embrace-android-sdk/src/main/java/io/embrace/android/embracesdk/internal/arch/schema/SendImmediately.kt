package io.embrace.android.embracesdk.internal.arch.schema

internal object SendImmediately : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "send_immediately")
    override val value: String = "true"
}
