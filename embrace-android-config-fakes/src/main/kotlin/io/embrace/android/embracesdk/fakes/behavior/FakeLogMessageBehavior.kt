package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehavior

class FakeLogMessageBehavior(
    private val logMessageMaximumAllowedLength: Int = 128,
    private val infoLogLimit: Int = 100,
    private val warnLogLimit: Int = 100,
    private val errorLogLimit: Int = 100,
) : LogMessageBehavior {

    override fun getLogMessageMaximumAllowedLength(): Int = logMessageMaximumAllowedLength
    override fun getInfoLogLimit(): Int = infoLogLimit
    override fun getWarnLogLimit(): Int = warnLogLimit
    override fun getErrorLogLimit(): Int = errorLogLimit
}
