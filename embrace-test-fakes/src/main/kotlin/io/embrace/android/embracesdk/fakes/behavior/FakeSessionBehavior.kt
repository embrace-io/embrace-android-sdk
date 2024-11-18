package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.SessionConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeSessionBehavior(
    private val maxSessionProperties: Int = 100,
    private val sessionControlEnabled: Boolean = false,
) : SessionBehavior {

    override val local: SessionConfig
        get() = throw UnsupportedOperationException()
    override val remote: RemoteConfig
        get() = throw UnsupportedOperationException()

    override fun getFullSessionEvents(): Set<String> = emptySet()
    override fun getSessionComponents(): Set<String>? = null
    override fun isGatingFeatureEnabled(): Boolean = false
    override fun isSessionControlEnabled(): Boolean = sessionControlEnabled
    override fun getMaxSessionProperties(): Int = maxSessionProperties
    override fun shouldGateInfoLog(): Boolean = false
    override fun shouldGateWarnLog(): Boolean = false
    override fun shouldSendFullForCrash(): Boolean = false
    override fun shouldSendFullForErrorLog(): Boolean = false
    override fun shouldGateSessionProperties(): Boolean = false
    override fun shouldGateLogProperties(): Boolean = false
}
