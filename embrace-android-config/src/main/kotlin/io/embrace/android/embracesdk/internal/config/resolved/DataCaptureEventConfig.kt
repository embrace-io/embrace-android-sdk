package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved data capture event config.
 */
interface DataCaptureEventConfig {
    val internalExceptionCaptureEnabled: Boolean

    /**
     * Regex patterns for event names and log messages that should not be captured, or null if none are disabled.
     */
    val disabledEventAndLogPatterns: Set<String>?
}

/**
 * Creates a [DataCaptureEventConfig]. A provider returning null means the default is used.
 */
inline fun DataCaptureEventConfig(
    crossinline internalExceptionCaptureEnabled: () -> Boolean? = { null },
    crossinline disabledEventAndLogPatterns: () -> Set<String>? = { null },
): DataCaptureEventConfig = object : DataCaptureEventConfig {
    override val internalExceptionCaptureEnabled: Boolean = internalExceptionCaptureEnabled() ?: true
    override val disabledEventAndLogPatterns: Set<String>? = disabledEventAndLogPatterns()
}
