package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior

class FakeSessionBehavior(
    private val maxSessionProperties: Int = 100,
    private val sessionControlEnabled: Boolean = false
) : SessionBehavior {

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
