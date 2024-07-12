package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface LogMessageBehavior {
    public fun getLogMessageMaximumAllowedLength(): Int
    public fun getInfoLogLimit(): Int
    public fun getWarnLogLimit(): Int
    public fun getErrorLogLimit(): Int
}
