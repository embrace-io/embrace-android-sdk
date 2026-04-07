package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior

class FakeSessionBehavior(
    private val maxSessionProperties: Int = 100,
    private val sessionControlEnabled: Boolean = false,
    private val maxSessionDurationMs: Long = 86400000,
    private val sessionInactivityTimeoutMs: Long = 1800000,
    private val minSessionDurationMs: Long = 5000,
) : SessionBehavior {

    override fun isSessionControlEnabled(): Boolean = sessionControlEnabled
    override fun getMaxSessionProperties(): Int = maxSessionProperties
    override fun getMaxSessionDurationMs(): Long = maxSessionDurationMs
    override fun getSessionInactivityTimeoutMs(): Long = sessionInactivityTimeoutMs
    override fun getMinSessionDurationMs(): Long = minSessionDurationMs
}
