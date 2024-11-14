package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.PatternCache
import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class DataCaptureEventBehaviorImpl(
    override val remote: RemoteConfig?,
) : DataCaptureEventBehavior {

    private companion object {
        private const val DEFAULT_INTERNAL_EXCEPTION_CAPTURE = true
    }

    override val local: UnimplementedConfig = null

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
