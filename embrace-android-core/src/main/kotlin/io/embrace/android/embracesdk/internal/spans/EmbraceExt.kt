package io.embrace.android.embracesdk.internal.spans

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Return the appropriate name used for telemetry created by Embrace given the current value
 */
public fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this
