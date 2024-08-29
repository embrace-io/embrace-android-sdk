package io.embrace.android.embracesdk.internal.arch.schema

public object SendImmediately : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "send_immediately")
    override val value: String = "true"
}
