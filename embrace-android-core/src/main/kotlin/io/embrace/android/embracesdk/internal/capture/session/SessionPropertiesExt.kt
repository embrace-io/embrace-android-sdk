package io.embrace.android.embracesdk.internal.capture.session

/**
 * Prefix added to all Embrace attribute keys that represent session properties that are set via the SDK
 */
private const val EMBRACE_SESSION_PROPERTY_NAME_PREFIX = "emb.properties."

fun String.toSessionPropertyAttributeName(): String = EMBRACE_SESSION_PROPERTY_NAME_PREFIX + this

fun String.isSessionPropertyAttributeName(): Boolean = startsWith(
    EMBRACE_SESSION_PROPERTY_NAME_PREFIX
)
