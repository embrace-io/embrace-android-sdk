package io.embrace.android.embracesdk.internal.arch.attrs

/**
 * Prefix added to all Embrace attribute keys that represent session properties that are set via the SDK
 */
private const val EMBRACE_SESSION_PROPERTY_NAME_PREFIX = "emb.properties."

fun String.toEmbraceAttributeName(): String = EMBRACE_SESSION_PROPERTY_NAME_PREFIX + this

fun String.isEmbraceAttributeName(): Boolean = startsWith(
    EMBRACE_SESSION_PROPERTY_NAME_PREFIX
)
