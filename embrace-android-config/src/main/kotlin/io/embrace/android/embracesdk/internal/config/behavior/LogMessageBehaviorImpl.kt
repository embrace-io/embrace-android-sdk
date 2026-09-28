package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.LogMessageConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveLogMessage

/**
 * Provides the behavior that should be followed for remote log message functionality.
 */
class LogMessageBehaviorImpl(private val config: LogMessageConfig) : LogMessageBehavior {

    constructor(remote: RemoteConfig?) : this(resolveLogMessage(behaviorInputs(InstrumentedConfigImpl, remote)))

    override fun getLogMessageMaximumAllowedLength(): Int = config.maxLength
    override fun getInfoLogLimit(): Int = config.infoLimit
    override fun getWarnLogLimit(): Int = config.warnLimit
    override fun getErrorLogLimit(): Int = config.errorLimit
}
