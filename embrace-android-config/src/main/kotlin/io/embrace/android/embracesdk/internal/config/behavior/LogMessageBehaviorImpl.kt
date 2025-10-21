package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that should be followed for remote log message functionality.
 */
class LogMessageBehaviorImpl(
    remote: RemoteConfig?,
) : LogMessageBehavior {

    private companion object {
        private const val DEFAULT_LOG_INFO_LIMIT = 100
        private const val DEFAULT_LOG_WARNING_LIMIT = 100
        private const val DEFAULT_LOG_ERROR_LIMIT = 250
    }

    override val remote: LogRemoteConfig? = remote?.logConfig
    override val local: UnimplementedConfig = null

    override fun getLogMessageMaximumAllowedLength(): Int {
        return remote?.logMessageMaximumAllowedLength ?: LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH
    }

    override fun getInfoLogLimit(): Int = remote?.logInfoLimit ?: DEFAULT_LOG_INFO_LIMIT
    override fun getWarnLogLimit(): Int = remote?.logWarnLimit ?: DEFAULT_LOG_WARNING_LIMIT
    override fun getErrorLogLimit(): Int = remote?.logErrorLimit ?: DEFAULT_LOG_ERROR_LIMIT
}
