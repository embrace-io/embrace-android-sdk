package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName

/**
 * Instance of a valid value for an attribute that has special meaning in the Embrace platform.
 */
internal interface EmbraceAttribute {
    /**
     * The unique name given to the attribute
     */
    val attributeName: String

    /**
     * The value of the particular instance of the attribute
     */
    val attributeValue: String

    fun otelAttributeName(): String = attributeName.toEmbraceAttributeName()
}
