package io.embrace.android.embracesdk.internal.spans

import io.opentelemetry.api.logs.Severity

/**
 * Prefix added to OTel signal object names recorded by the SDK
 */
private const val EMBRACE_OBJECT_NAME_PREFIX = "emb-"

/**
 * Prefix added to all attribute keys for all usage attributes added by the SDK
 */
private const val EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX = "emb.usage."

/**
 * Return the appropriate name used for telemetry created by Embrace given the current value
 */
public fun String.toEmbraceObjectName(): String = EMBRACE_OBJECT_NAME_PREFIX + this

public fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): Severity = when (this) {
    io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
    io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
    io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
}

/**
 * Return the appropriate internal Embrace attribute usage name given the current string
 */
internal fun String.toEmbraceUsageAttributeName(): String = EMBRACE_USAGE_ATTRIBUTE_NAME_PREFIX + this
