package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionIdsProviderImplTest {

    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var sessionPartTracker: FakeSessionPartTracker
    private lateinit var provider: SessionIdsProviderImpl

    @Before
    fun setUp() {
        sessionOrchestrator = FakeSessionOrchestrator()
        sessionPartTracker = FakeSessionPartTracker()
        provider = SessionIdsProviderImpl({ sessionOrchestrator }, sessionPartTracker)
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
        sessionOrchestrator.currentSession = UserSessionMetadata.Classified(
            startTimeMs = 1000L,
            userSessionId = "user-session-uuid",
            userSessionNumber = 1L,
            maxDurationSecs = 3600L,
            inactivityTimeoutSecs = 300L,
            partIndex = 1,
            lastActivityMs = 1000L,
            isBackgroundOnly = false,
        )
        assertEquals("user-session-uuid", provider.getCurrentUserSessionId())
    }

    @Test
    fun `getActiveSessionIds returns IDs from session parts tracker even if current session per the orchestrator differs`() {
        val token = fakeSessionPartToken()
        sessionPartTracker.currentSession = token
        sessionOrchestrator.currentSession = UserSessionMetadata.Classified(
            startTimeMs = token.startTime + 10_000L,
            userSessionId = "new-user-session",
            userSessionNumber = 2,
            maxDurationSecs = 3600L,
            inactivityTimeoutSecs = 300L,
            partIndex = token.userSessionPartIndex + 1,
            lastActivityMs = token.startTime + 10_000L,
            isBackgroundOnly = false,
        )
        assertEquals(SessionIdsSnapshot(token.userSessionId, token.sessionPartId), provider.getActiveSessionIds())
    }

    @Test
    fun `getActiveSessionIds falls back to the orchestrator's user session id when no active session part`() {
        sessionPartTracker.currentSession = null
        sessionOrchestrator.currentSession = UserSessionMetadata.Classified(
            startTimeMs = 1000L,
            userSessionId = "user-session-uuid",
            userSessionNumber = 1L,
            maxDurationSecs = 3600L,
            inactivityTimeoutSecs = 300L,
            partIndex = 1,
            lastActivityMs = 1000L,
            isBackgroundOnly = false,
        )
        assertEquals(
            SessionIdsSnapshot(userSessionId = "user-session-uuid", sessionPartId = ""),
            provider.getActiveSessionIds(),
        )
    }
}
