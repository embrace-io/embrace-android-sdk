package io.embrace.android.embracesdk.internal.arch.schema

/**
 * Prefix added to all attribute keys for all attributes with a specific meaning to the Embrace platform
 */
private const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

/**
 * Prefix added to all Embrace attribute keys that are meant to be internal to Embrace
 */
private const val EMBRACE_PRIVATE_ATTRIBUTE_NAME_PREFIX = "emb.private."

/**
 * Return the appropriate internal Embrace attribute name given the current string
 */
public fun String.toEmbraceAttributeName(isPrivate: Boolean = false): String {
    val prefix = if (isPrivate) {
        EMBRACE_PRIVATE_ATTRIBUTE_NAME_PREFIX
    } else {
        EMBRACE_ATTRIBUTE_NAME_PREFIX
    }
    return prefix + this
}
