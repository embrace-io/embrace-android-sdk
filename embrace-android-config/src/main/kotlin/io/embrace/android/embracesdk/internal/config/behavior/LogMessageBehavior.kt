package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.resolved.LogMessageConfig

interface LogMessageBehavior {
    fun getLogMessageMaximumAllowedLength(): Int
    fun getInfoLogLimit(): Int
    fun getWarnLogLimit(): Int
    fun getErrorLogLimit(): Int
}

const val LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH: Int = LogMessageConfig.DEFAULT_MAX_LENGTH
