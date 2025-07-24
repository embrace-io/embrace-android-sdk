package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Declares the limits for OTel data capture.
 *
 * Currently this is not instrumented by the gradle plugin so the values won't change - that will
 * be implemented in a future PR.
 */
interface OtelLimitsConfig {
    fun getMaxInternalNameLength(): Int = 2000
    fun getMaxNameLength(): Int = 128
    fun getMaxCustomEventCount(): Int = 10
    fun getMaxSystemEventCount(): Int = 11000
    fun getMaxCustomAttributeCount(): Int = 100
    fun getMaxSystemAttributeCount(): Int = 300
    fun getMaxCustomLinkCount(): Int = 10
    fun getMaxSystemLinkCount(): Int = 100
    fun getMaxInternalAttributeKeyLength(): Int = 1000
    fun getMaxInternalAttributeValueLength(): Int = 2000
    fun getMaxCustomAttributeKeyLength(): Int = 128
    fun getMaxCustomAttributeValueLength(): Int = 1024
    fun getExceptionEventName(): String = "exception"
}
