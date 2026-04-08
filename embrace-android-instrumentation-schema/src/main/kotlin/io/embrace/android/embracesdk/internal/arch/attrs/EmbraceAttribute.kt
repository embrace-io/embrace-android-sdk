package io.embrace.android.embracesdk.internal.arch.attrs

/**
 * Embrace-specific implementation of an OTel Attribute. This contains a key and a String value
 * (other types are not supported yet).
 */
interface EmbraceAttribute {

    val key: String

    /**
     * The value of the particular instance of the attribute
     */
    val value: String
}
