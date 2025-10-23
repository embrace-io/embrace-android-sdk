package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior

class FakeSessionBehavior(
    private val maxSessionProperties: Int = 100,
    private val sessionControlEnabled: Boolean = false,
) : SessionBehavior {

    override fun isSessionControlEnabled(): Boolean = sessionControlEnabled
    override fun getMaxSessionProperties(): Int = maxSessionProperties
}
