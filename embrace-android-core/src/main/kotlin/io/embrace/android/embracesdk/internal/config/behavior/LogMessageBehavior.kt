package io.embrace.android.embracesdk.internal.config.behavior

public interface LogMessageBehavior {
    public fun getLogMessageMaximumAllowedLength(): Int
    public fun getInfoLogLimit(): Int
    public fun getWarnLogLimit(): Int
    public fun getErrorLogLimit(): Int
}
