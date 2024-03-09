package io.embrace.android.embracesdk.arch.schema

/**
 * Denotes an important span to be aggregated and displayed as such in the platform.
 */
internal object KeySpan : EmbraceAttribute {
    override val attributeName: String = "key"
    override val attributeValue: String = "true"
}
