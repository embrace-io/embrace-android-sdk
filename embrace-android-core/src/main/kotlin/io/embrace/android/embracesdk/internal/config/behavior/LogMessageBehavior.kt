package io.embrace.android.embracesdk.internal.config.behavior

public interface LogMessageBehavior {
    public fun getLogMessageMaximumAllowedLength(): Int
    public fun getInfoLogLimit(): Int
    public fun getWarnLogLimit(): Int
    public fun getErrorLogLimit(): Int
}
public const val LOG_MESSAGE_MAXIMUM_ALLOWED_LENGTH: Int = 128
