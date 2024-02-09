package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.PatternCache
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UnimplementedConfig

internal class DataCaptureEventBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?> = { null }
) : MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck,
    { null },
    remoteSupplier
) {

    companion object {
        private const val DEFAULT_INTERNAL_EXCEPTION_CAPTURE = true
    }

    private val patternCache = PatternCache()

    fun isInternalExceptionCaptureEnabled(): Boolean =
        remote?.internalExceptionCaptureEnabled ?: DEFAULT_INTERNAL_EXCEPTION_CAPTURE

    fun isEventEnabled(eventName: String): Boolean {
        return when (val disabledTypes = remote?.disabledEventAndLogPatterns) {
            null -> true
            else -> !patternCache.doesStringMatchesPatternInSet(eventName, disabledTypes)
        }
    }

    fun isLogMessageEnabled(logMessage: String): Boolean {
        return when (val disabledTypes = remote?.disabledEventAndLogPatterns) {
            null -> true
            else -> !patternCache.doesStringMatchesPatternInSet(logMessage, disabledTypes)
        }
    }

    fun getEventLimits(): Map<String, Long> = remote?.eventLimits ?: emptyMap()
}
