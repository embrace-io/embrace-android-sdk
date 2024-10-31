package io.embrace.android.embracesdk.internal.arch.schema

import io.opentelemetry.api.common.AttributeKey

/**
 * Defines a unique object to represent a [EmbraceAttribute]
 */
class EmbraceAttributeKey(
    override val id: String,
    otelAttributeKey: AttributeKey<String>? = null,
    useIdAsAttributeName: Boolean = false,
    isPrivate: Boolean = false,
) : EmbraceAttribute {
    override val name: String = if (!useIdAsAttributeName && otelAttributeKey?.key != null) {
        otelAttributeKey.key
    } else {
        id.toEmbraceAttributeName(isPrivate)
    }

    /**
     * The [AttributeKey] equivalent for this object to be used in conjunction with OTel objects
     */
    val attributeKey: AttributeKey<String> = otelAttributeKey ?: AttributeKey.stringKey(name)
}
