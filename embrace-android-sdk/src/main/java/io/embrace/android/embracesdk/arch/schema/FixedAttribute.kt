package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.payload.Attribute

/**
 * Instance of a fixed key-value pair for a attribute, used for low cardinality dimensions with a known, fixed set of valid values.
 */
@InternalApi
public interface FixedAttribute {

    public val key: EmbraceAttributeKey

    /**
     * The value of the particular instance of the attribute
     */
    public val value: String

    /**
     * Return as a key-value pair appropriate to use as an OpenTelemetry attribute
     */
    public fun toEmbraceKeyValuePair(): Pair<String, String> = Pair(key.name, value)

    /**
     * Return as a [Attribute] representation, to be used used for Embrace payloads
     */
    public fun toPayload(): Attribute = Attribute(key.name, value)
}
