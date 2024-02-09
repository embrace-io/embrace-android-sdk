package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.utils.UnimplementedConfig

/**
 * Provides the behavior that should be followed for remote log message functionality.
 */
internal class LogMessageBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: () -> LogRemoteConfig?
) : MergedConfigBehavior<UnimplementedConfig, LogRemoteConfig>(
    thresholdCheck,
    { null },
    remoteSupplier
) {

    companion object {
        private const val DEFAULT_LOG_INFO_LIMIT = 100
        private const val DEFAULT_LOG_WARNING_LIMIT = 100
        private const val DEFAULT_LOG_ERROR_LIMIT = 250
        internal const val LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH = 128
    }

    fun getLogMessageMaximumAllowedLength(): Int {
        return remote?.logMessageMaximumAllowedLength ?: LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH
    }

    fun getInfoLogLimit(): Int = remote?.logInfoLimit ?: DEFAULT_LOG_INFO_LIMIT
    fun getWarnLogLimit(): Int = remote?.logWarnLimit ?: DEFAULT_LOG_WARNING_LIMIT
    fun getErrorLogLimit(): Int = remote?.logErrorLimit ?: DEFAULT_LOG_ERROR_LIMIT
}
