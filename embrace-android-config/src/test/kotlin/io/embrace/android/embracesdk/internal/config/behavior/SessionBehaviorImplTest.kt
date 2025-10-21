package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SessionBehaviorImplTest {

    private val remote = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            isEnabled = true,
        ),
        maxSessionProperties = 57,
    )

    @Test
    fun testDefaults() {
        with(createSessionBehavior()) {
            assertEquals(100, getMaxSessionProperties())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createSessionBehavior(remoteCfg = remote)) {
            assertEquals(57, getMaxSessionProperties())
        }
    }

    @Test
    fun `remote session properties limit is capped to 200`() {
        with(createSessionBehavior(remoteCfg = RemoteConfig(maxSessionProperties = 1000))) {
            assertEquals(200, getMaxSessionProperties())
        }
    }
}
