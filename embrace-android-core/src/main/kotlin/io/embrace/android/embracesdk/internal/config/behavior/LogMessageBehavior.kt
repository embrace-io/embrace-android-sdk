package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig

interface LogMessageBehavior : ConfigBehavior<UnimplementedConfig, LogRemoteConfig> {
    fun getLogMessageMaximumAllowedLength(): Int
    fun getInfoLogLimit(): Int
    fun getWarnLogLimit(): Int
    fun getErrorLogLimit(): Int
}

const val LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH: Int = 128
