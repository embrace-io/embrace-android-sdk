package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName
import io.opentelemetry.api.common.AttributeKey

/**
 * An attribute to be used in telemetry objects and payload envelopes
 */
internal interface EmbraceAttribute {
    /**
     * The unique name given to the attribute.
     * Don't use this to look up the existence of an attribute in a log or span - use [name] instead
     */
    val id: String

    /**
     * Return the appropriate name for this attribute to be use in the representation of a telemetry object
     */
    val name: String
}

/**
 * Defines a unique object to represent a [EmbraceAttribute]
 */
internal class EmbraceAttributeKey(
    override val id: String,
    otelAttributeKey: AttributeKey<String>? = null,
    useIdAsAttributeName: Boolean = false
) : EmbraceAttribute {
    override val name: String = if (!useIdAsAttributeName && otelAttributeKey?.key != null) {
        otelAttributeKey.key
    } else {
        id.toEmbraceAttributeName()
    }

    /**
     * The [AttributeKey] equivalent for this object to be used in conjunction with OTel objects
     */
    val attributeKey: AttributeKey<String> = otelAttributeKey ?: AttributeKey.stringKey(name)
}

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
