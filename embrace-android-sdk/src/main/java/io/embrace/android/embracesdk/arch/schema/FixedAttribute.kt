package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.payload.Attribute

/**
 * Instance of a fixed key-value pair for a attribute, used for low cardinality dimensions with a known, fixed set of valid values.
 */
internal interface FixedAttribute {

    val key: EmbraceAttributeKey

    /**
     * The value of the particular instance of the attribute
     */
    val value: String

    /**
     * Return as a key-value pair appropriate to use as an OpenTelemetry attribute
     */
    fun toEmbraceKeyValuePair() = Pair(key.name, value)

    /**
     * Return as a [Attribute] representation, to be used used for Embrace payloads
     */
    fun toPayload() = Attribute(key.name, value)
}
