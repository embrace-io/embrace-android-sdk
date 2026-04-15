package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class UserSessionBehaviorImplTest {

    private val remote = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            isEnabled = true,
        ),
        maxUserSessionProperties = 57,
    )

    private val defaultMaxDurationMs = 12 * 3600 * 1_000L
    private val defaultInactivityTimeoutMs = 30 * 60 * 1_000L

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
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 43200))
        assertEquals(43200 * 1_000L, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `max session duration defaults when remote value is zero`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 0))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `max session duration defaults when remote value is negative`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = -1))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `max session duration defaults when remote value is below 1 hour`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 3599))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `max session duration is valid at exactly 1 hour`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 3600))
        assertEquals(3600 * 1_000L, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `inactivity timeout uses valid remote value`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 86400, inactivityTimeoutSeconds = 1200))
        assertEquals(1200 * 1_000L, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when remote value is below minimum`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 86400, inactivityTimeoutSeconds = 0))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout is valid when remote value equals minimum`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 86400, inactivityTimeoutSeconds = 30))
        assertEquals(30 * 1_000L, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when remote value is negative`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 86400, inactivityTimeoutSeconds = -5))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when it exceeds max duration`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 7200, inactivityTimeoutSeconds = 10800))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout is valid when equal to max duration`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 7200, inactivityTimeoutSeconds = 7200))
        assertEquals(7200 * 1_000L, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `inactivity timeout defaults when max duration is invalid and configured timeout exceeds default max`() {
        val cfg =
            RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 0, inactivityTimeoutSeconds = 120000))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }

    @Test
    fun `max session duration is capped at 24h when remote value exceeds 24h`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(maxDurationSeconds = 200000))
        assertEquals(defaultMaxDurationMs, createSessionBehavior(remoteCfg = cfg).getMaxSessionDurationMs())
    }

    @Test
    fun `inactivity timeout is capped at 24h when remote value exceeds 24h`() {
        val cfg = RemoteConfig(userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = 200000))
        assertEquals(defaultInactivityTimeoutMs, createSessionBehavior(remoteCfg = cfg).getSessionInactivityTimeoutMs())
    }
}
