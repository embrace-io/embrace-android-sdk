package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SessionBehaviorImplTest {

    private val remote = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            isEnabled = true,
        ),
        maxUserSessionProperties = 57,
    )

    private val defaultMaxDurationMs = 24 * 60 * 60_000L
    private val defaultInactivityTimeoutMs = 30 * 60_000L

    @Test
    fun testDefaults() {
        with(createSessionBehavior()) {
            assertEquals(100, getMaxUserSessionProperties())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createSessionBehavior(remoteCfg = remote)) {
            assertEquals(57, getMaxUserSessionProperties())
        }
    }

    @Test
    fun `remote session properties limit is capped to 200`() {
        with(createSessionBehavior(remoteCfg = RemoteConfig(maxUserSessionProperties = 1000))) {
            assertEquals(200, getMaxUserSessionProperties())
        }
    }

    @Test
    fun `user session defaults`() {
        assertEquals(defaultMaxDurationMs, createSessionBehavior().getMaxSessionDurationMs())
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior().getSessionInactivityTimeoutMs())
    }

    @Test
    fun `max session duration uses valid remote value`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 720))
        assertEquals(720 * 60_000L, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `max session duration defaults when remote value is zero`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 0))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `max session duration defaults when remote value is negative`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = -1))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `inactivity timeout uses valid remote value`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 1440, inactivityTimeoutMinutes = 20))
        assertEquals(20 * 60_000L, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when remote value is zero`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 1440, inactivityTimeoutMinutes = 0))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when remote value is negative`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 1440, inactivityTimeoutMinutes = -5))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when it exceeds max duration`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 20, inactivityTimeoutMinutes = 30))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout is valid when equal to max duration`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 30, inactivityTimeoutMinutes = 30))
        assertEquals(30 * 60_000L, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when max duration is invalid and configured timeout exceeds default max`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationMinutes = 0, inactivityTimeoutMinutes = 2000))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }
}
