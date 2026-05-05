package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionIdProviderImplTest {

    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var sessionPartTracker: FakeSessionPartTracker
    private lateinit var provider: SessionIdProviderImpl

    @Before
    fun setUp() {
        sessionOrchestrator = FakeSessionOrchestrator()
        sessionPartTracker = FakeSessionPartTracker()
        provider = SessionIdProviderImpl({ sessionOrchestrator }, sessionPartTracker)
    }

    @Test
    fun `returns empty strings when no active user session or session part`() {
        assertEquals("", provider.getCurrentSessionPartId())
        assertEquals("", provider.getCurrentUserSessionId())
        assertEquals(SessionIdsSnapshot(userSessionId = "", sessionPartId = ""), provider.getActiveSessionIds())
    }

    @Test
    fun `returns session part id when active session part exists`() {
        sessionPartTracker.currentSession = fakeSessionPartToken()
        assertEquals("fakeSessionPartId", provider.getCurrentSessionPartId())
    }

    @Test
    fun `getCurrentUserSessionId returns the orchestrator's active user session id`() {
        sessionOrchestrator.currentSession = UserSessionMetadata(
            startTimeMs = 1000L,
            userSessionId = "user-session-uuid",
            userSessionNumber = 1L,
            maxDurationSecs = 3600L,
            inactivityTimeoutSecs = 300L,
            partNumber = 1,
            lastActivityMs = 1000L,
        )
        assertEquals("user-session-uuid", provider.getCurrentUserSessionId())
    }

    @Test
    fun `getActiveSessionIds returns IDs from current session`() {
        val token = fakeSessionPartToken()
        sessionPartTracker.currentSession = token
        assertEquals(SessionIdsSnapshot(token.userSessionId, token.sessionPartId), provider.getActiveSessionIds())
    }

    @Test
    fun `getActiveSessionIds falls back to the orchestrator's user session id when no active session part`() {
        sessionPartTracker.currentSession = null
        sessionOrchestrator.currentSession = UserSessionMetadata(
            startTimeMs = 1000L,
            userSessionId = "user-session-uuid",
            userSessionNumber = 1L,
            maxDurationSecs = 3600L,
            inactivityTimeoutSecs = 300L,
            partNumber = 1,
            lastActivityMs = 1000L,
        )
        assertEquals(
            SessionIdsSnapshot(userSessionId = "user-session-uuid", sessionPartId = ""),
            provider.getActiveSessionIds(),
        )
    }
}
