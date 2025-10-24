package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.PatternCache
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class DataCaptureEventBehaviorImpl(
    private val remote: RemoteConfig?,
) : DataCaptureEventBehavior {

    private companion object {
        private const val DEFAULT_INTERNAL_EXCEPTION_CAPTURE = true
    }

    private val patternCache = PatternCache()

    override fun isInternalExceptionCaptureEnabled(): Boolean =
        remote?.internalExceptionCaptureEnabled ?: DEFAULT_INTERNAL_EXCEPTION_CAPTURE

    override fun isEventEnabled(eventName: String): Boolean {
        return when (val disabledTypes = remote?.disabledEventAndLogPatterns) {
            null -> true
            else -> !patternCache.doesStringMatchesPatternInSet(eventName, disabledTypes)
        }
    }

    override fun isLogMessageEnabled(logMessage: String): Boolean {
        return when (val disabledTypes = remote?.disabledEventAndLogPatterns) {
            null -> true
            else -> !patternCache.doesStringMatchesPatternInSet(logMessage, disabledTypes)
        }
    }
}
