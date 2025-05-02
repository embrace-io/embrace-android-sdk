package io.embrace.android.embracesdk.internal.arch.schema

/**
 * Defines a unique object to represent a [EmbraceAttribute]
 */
class EmbraceAttributeKey(
    override val id: String,
    otelAttributeKey: String? = null,
    useIdAsAttributeName: Boolean = false,
    isPrivate: Boolean = false,
) : EmbraceAttribute {
    override val name: String = if (!useIdAsAttributeName && otelAttributeKey != null) {
        otelAttributeKey
    } else {
        id.toEmbraceAttributeName(isPrivate)
    }

    /**
     * The key equivalent for this object to be used in conjunction with OTel objects
     */
    val attributeKey: String = otelAttributeKey ?: name
}
