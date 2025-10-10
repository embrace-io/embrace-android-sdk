package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeSessionBehavior(
    private val maxSessionProperties: Int = 100,
    private val sessionControlEnabled: Boolean = false,
) : SessionBehavior {

    override val local: Unit = Unit
    override val remote: RemoteConfig
        get() = throw UnsupportedOperationException()

    override fun isSessionControlEnabled(): Boolean = sessionControlEnabled
    override fun getMaxSessionProperties(): Int = maxSessionProperties
}
