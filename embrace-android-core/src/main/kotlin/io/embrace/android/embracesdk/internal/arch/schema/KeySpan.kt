package io.embrace.android.embracesdk.internal.arch.schema

/**
 * Denotes an important span to be aggregated and displayed as such in the platform.
 */
public object KeySpan : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "key")
    override val value: String = "true"
}
