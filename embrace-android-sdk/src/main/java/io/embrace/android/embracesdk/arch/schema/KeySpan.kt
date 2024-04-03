package io.embrace.android.embracesdk.arch.schema

/**
 * Denotes an important span to be aggregated and displayed as such in the platform.
 */
internal object KeySpan : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "key")
    override val value: String = "true"
}
