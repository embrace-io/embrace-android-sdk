package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.UserSessionConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveUserSession

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
class UserSessionBehaviorImpl(private val config: UserSessionConfig) : UserSessionBehavior {

    constructor(remote: RemoteConfig?) : this(resolveUserSession(behaviorInputs(InstrumentedConfigImpl, remote)))

    override fun isSessionControlEnabled(): Boolean = config.sessionControlEnabled
    override fun getMaxUserSessionProperties(): Int = config.sessionPropertyLimit
    override fun getMaxSessionDurationMs(): Long = config.maxDurationSeconds * 1000L
    override fun getSessionInactivityTimeoutMs(): Long = config.inactivityTimeoutSeconds * 1000L
    override fun getMinSessionDurationMs(): Long = config.minSessionDurationMs
}
