package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.PatternCache
import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

class DataCaptureEventBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?> = { null },
) : DataCaptureEventBehavior, MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {

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

    override fun getEventLimits(): Map<String, Long> = remote?.eventLimits ?: emptyMap()
}
