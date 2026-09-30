package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.PatternCache
import io.embrace.android.embracesdk.internal.config.resolved.DataCaptureEventConfig

/**
 * Decides whether events and logs should be captured, based on the resolved [DataCaptureEventConfig].
 */
class DataCaptureEventBehavior(
    private val config: DataCaptureEventConfig,
) {

    private val patternCache = PatternCache()

    fun isEventEnabled(eventName: String): Boolean = isEnabled(eventName)

    fun isLogMessageEnabled(logMessage: String): Boolean = isEnabled(logMessage)

    private fun isEnabled(value: String): Boolean {
        return when (val disabledTypes = config.disabledEventAndLogPatterns) {
            null -> true
            else -> !patternCache.doesStringMatchPatternInSet(value, disabledTypes)
        }
    }
}
