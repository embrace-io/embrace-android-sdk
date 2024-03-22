package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName

/**
 * Instance of a valid value for an attribute that has special meaning in the Embrace platform.
 */
internal interface EmbraceAttribute {
    /**
     * The unique name given to the attribute.
     * Don't use this to look up the existence of an attribute in a log or span - use [otelAttributeName] instead
     */
    val attributeName: String

    /**
     * The value of the particular instance of the attribute
     */
    val attributeValue: String

    /**
     * Return the appropriate key name for this attribute to use in an OpenTelemetry attribute
     */
    fun otelAttributeName(): String = attributeName.toEmbraceAttributeName()

    /**
     * Return attribute as a key-value pair appropriate to use as an OpenTelemetry attribute
     */
    fun toOTelKeyValuePair() = Pair(otelAttributeName(), attributeValue)

    /**
     * Return attribute as [Attribute]
     */
    fun toAttributePayload() = Attribute(otelAttributeName(), attributeValue)
}
